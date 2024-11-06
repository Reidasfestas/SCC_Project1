package tukano.impl.storage;

import java.util.Arrays;
import java.util.function.Consumer;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

import static tukano.api.Result.error;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.CONFLICT;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.ErrorCode.NOT_FOUND;

import AzureSetUp.AzureProperties;
import tukano.api.Result;
import utils.Hash;
public class AzureBlobStorage implements BlobStorage{

    private final BlobContainerClient primaryContainerClient;
    private BlobContainerClient secondaryContainerClient = null;

    //private static final String primaryStorageConnectionString = "DefaultEndpointsProtocol=https;AccountName=sto60019northeurope;AccountKey=/Gk4NLhMWIQawS7pW3vNQbli+hIt3avvoNZGZ6LziM8opgT2YSGglYxAQLXWr4+zXfnCQoarqSOE+AStD2u5rg==;EndpointSuffix=core.windows.net";
    //private static final String secondaryStorageConnectionString = "DefaultEndpointsProtocol=https;AccountName=sto60019northeurope-secondary;AccountKey=/Gk4NLhMWIQawS7pW3vNQbli+hIt3avvoNZGZ6LziM8opgT2YSGglYxAQLXWr4+zXfnCQoarqSOE+AStD2u5rg==;EndpointSuffix=core.windows.net";

    private static final String BLOBS_CONTAINER_NAME = "shorts";
    private static boolean SECONDARY_REGION = true;

    public AzureBlobStorage() {
        primaryContainerClient = new BlobContainerClientBuilder()
                .connectionString(AzureProperties.getInstance().getBlobKey())
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();

        if (AzureProperties.getInstance().isBlobSecondaryRegionEnabled()) {
            String secondaryStorageConnectionString = formatSecondaryConnectionString(AzureProperties.getInstance().getBlobKey());
            secondaryContainerClient = new BlobContainerClientBuilder()
                    .connectionString(secondaryStorageConnectionString)
                    .containerName(BLOBS_CONTAINER_NAME)
                    .buildClient();
        } else {
            secondaryContainerClient = null; // Initialize as null if secondary is not used
        }
    }

    private static String formatSecondaryConnectionString(String primaryConnectionString) {
        String[] parts = primaryConnectionString.split(";");
        StringBuilder secondaryConnectionString = new StringBuilder();

        for (String part : parts) {
            if (part.startsWith("AccountName=")) {
                secondaryConnectionString.append("AccountName=")
                        .append(part.substring("AccountName=".length()))
                        .append("-secondary;");
            } else {
                secondaryConnectionString.append(part).append(";");
            }
        }

        // Remove the last unnecessary ";" at the end
        return secondaryConnectionString.toString().replaceAll(";$", "");
    }


    @Override
    public Result<Void> write(String path, byte[] bytes) {
        if (path == null)
            return error(BAD_REQUEST);

        BlobClient blob = primaryContainerClient.getBlobClient(path);
        if (blob.exists()) {
            if (Arrays.equals(Hash.sha256(bytes), Hash.sha256(read(path).value())))
                return ok();
            else
                return error(CONFLICT);
        }

        blob.upload(BinaryData.fromBytes(bytes));

        return ok();
    }


    @Override
    public Result<byte[]> read(String path) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            // Attempt to read from the primary region
            BlobClient blob = primaryContainerClient.getBlobClient(path);
            if (!blob.exists()) {
                return error(NOT_FOUND);
            }

            var bytes = blob.downloadContent().toBytes();
            if (bytes != null) {
                return ok(bytes);
            }

            throw new Exception("Failed to read from primary region.");
        } catch (Exception e) {
            // If primary read fails, attempt to read from the secondary region
            if (AzureProperties.getInstance().isBlobSecondaryRegionEnabled()) {
                BlobClient blob = secondaryContainerClient.getBlobClient(path);
                if (!blob.exists()) {
                    return error(NOT_FOUND);
                }

                var bytes = blob.downloadContent().toBytes();
                return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
            }
        }
        return error(INTERNAL_ERROR);
    }


    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null)
            return error(BAD_REQUEST);

        try {
            // Attempt to read from the primary region
            BlobClient blob = primaryContainerClient.getBlobClient(path);
            if (!blob.exists()) {
                return error(NOT_FOUND);
            }

            var bytes = blob.downloadContent().toBytes();
            if (bytes != null) {
                sink.accept(bytes);
                return Result.ok();
            }

            throw new Exception("Failed to read from primary region.");
        } catch (Exception e) {
            // If primary read fails, attempt to read from the secondary region
            if (AzureProperties.getInstance().isBlobSecondaryRegionEnabled()) {
                BlobClient blob = secondaryContainerClient.getBlobClient(path);
                if (!blob.exists()) {
                    return error(NOT_FOUND);
                }

                var bytes = blob.downloadContent().toBytes();
                if (bytes != null) {
                    sink.accept(bytes);
                    return Result.ok();
                }
                return error(INTERNAL_ERROR);
            }
        }
        return error(INTERNAL_ERROR);
    }

    @Override
    public Result<Void> delete(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        BlobClient blob = primaryContainerClient.getBlobClient(path);
        if (blob.exists()) {
            blob.delete();
            return Result.ok();
        } else
            return error(NOT_FOUND);
    }

    @Override
    public Result<Void> deleteAll(String path) {
        if (path == null) {
            return error(BAD_REQUEST);
        }
        if (!path.endsWith("/")) {
            path += "/";
        }
        try {
            primaryContainerClient.listBlobsByHierarchy(path).forEach(blobItem -> {
                BlobClient blobClient = primaryContainerClient.getBlobClient(blobItem.getName());
                blobClient.delete();
            });

            return ok();
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

}