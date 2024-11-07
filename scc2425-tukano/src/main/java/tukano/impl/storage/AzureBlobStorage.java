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


    private static final String BLOBS_CONTAINER_NAME = "shorts";

    public AzureBlobStorage() {
        primaryContainerClient = new BlobContainerClientBuilder()
                .connectionString(AzureProperties.getInstance().getBlobKey())
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();

        if (AzureProperties.getInstance().isBlobSecondaryRegionEnabled()) {
            secondaryContainerClient = new BlobContainerClientBuilder()
                    .connectionString(AzureProperties.getInstance().getSecondaryBlobKey())
                    .containerName(BLOBS_CONTAINER_NAME)
                    .buildClient();
        } else {
            secondaryContainerClient = null; // Initialize as null if secondary is not used
        }
    }

    private void replicateToSecondary(Runnable operation) {
        if (AzureProperties.getInstance().isBlobSecondaryRegionEnabled()) {
            new Thread(operation).start();
        }
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

        replicateToSecondary(() -> {
            if (secondaryContainerClient != null) {
                BlobClient secondaryBlob = secondaryContainerClient.getBlobClient(path);
                if(!secondaryBlob.exists()) {
                    secondaryBlob.upload(BinaryData.fromBytes(bytes), true);
                }
            }
        });

        return ok();
    }


    @Override
    public Result<byte[]> read(String path) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            BlobClient blob = primaryContainerClient.getBlobClient(path);
            if (!blob.exists()) {
                return error(NOT_FOUND);
            }

            var bytes = blob.downloadContent().toBytes();
            return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
        } catch (Exception e) {
            if (secondaryContainerClient != null) {
                BlobClient blob = secondaryContainerClient.getBlobClient(path);
                if (blob.exists()) {
                    var bytes = blob.downloadContent().toBytes();
                    return bytes != null ? ok(bytes) : error(INTERNAL_ERROR);
                }
            }
        }
        return error(INTERNAL_ERROR);
    }


    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        try {
            BlobClient blob = primaryContainerClient.getBlobClient(path);
            if (!blob.exists()) {
                return error(NOT_FOUND);
            }

            var bytes = blob.downloadContent().toBytes();
            if (bytes != null) {
                sink.accept(bytes);
                return Result.ok();
            }
        } catch (Exception e) {
            if (secondaryContainerClient != null) {
                BlobClient blob = secondaryContainerClient.getBlobClient(path);
                if (blob.exists()) {
                    var bytes = blob.downloadContent().toBytes();
                    if (bytes != null) {
                        sink.accept(bytes);
                        return Result.ok();
                    }
                }
            }
        }
        return error(INTERNAL_ERROR);
    }


    @Override
    public Result<Void> delete(String path) {
        if (path == null) {
            return error(BAD_REQUEST);
        }

        BlobClient blob = primaryContainerClient.getBlobClient(path);
        if (blob.exists()) {
            blob.delete();
            replicateToSecondary(() -> {
                if (secondaryContainerClient != null) {
                    BlobClient secondaryBlob = secondaryContainerClient.getBlobClient(path);
                    if (secondaryBlob.exists()) {
                        secondaryBlob.delete();
                    }
                }
            });
            return Result.ok();
        } else {
            return error(NOT_FOUND);
        }
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

            String finalPath = path;
            replicateToSecondary(() -> {
                if (secondaryContainerClient != null) {
                    secondaryContainerClient.listBlobsByHierarchy(finalPath).forEach(blobItem -> {
                        BlobClient secondaryBlobClient = secondaryContainerClient.getBlobClient(blobItem.getName());
                        secondaryBlobClient.delete();
                    });
                }
            });

            return ok();
        } catch (Exception e) {
            return error(INTERNAL_ERROR);
        }
    }

}