package main.java.tukano.impl.storage;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import main.java.tukano.api.Result;

import java.nio.file.Path;
import java.util.function.Consumer;

import static main.java.tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static main.java.tukano.api.Result.error;
import static main.java.tukano.api.Result.ok;

public class AzureBlobStorage implements BlobStorage {

    private static final String STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=scc60019;AccountKey=3vdECwEpzQ4wfZ2Vd8NpMbXN2P6rsmlcf9tEiExsbAzqdZridFT2GPHcmUco2C+YddytZta8zcqJ+AStmuK9Kw==;EndpointSuffix=core.windows.net";
    private static final String BLOBS_CONTAINER_NAME = "files";
    private final BlobContainerClient containerClient;


    public AzureBlobStorage() {
        containerClient = new BlobContainerClientBuilder()
                .connectionString(STORAGE_CONNECTION_STRING)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();
    }


    @Override
    public Result<Void> write(String path, byte[] bytes) {
        try {
            // Get client to blob
            BlobClient blob = containerClient.getBlobClient( path );

            // Upload contents from BinaryData (check documentation for other alternatives)
            blob.upload(BinaryData.fromBytes(bytes));

            System.out.println( "File uploaded : " + path);

            return ok();

        } catch( Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> delete(String path) {
        try {
            // Get client to blob
            BlobClient blob = containerClient.getBlobClient(path);

            // Delete the blob
            blob.delete();

            System.out.println("Blob deleted: " + path);

            // Return success result with no data (Void)
            return ok(null);
        } catch (Exception e) {
            e.printStackTrace();
            // Assuming `fail` is a helper function to return failure with the exception.
            return error(INTERNAL_ERROR);
        }
    }


    @Override
    public Result<byte[]> read(String path) {
        try {
            BlobClient blob = containerClient.getBlobClient(path);

            // Download contents to BinaryData (check documentation for other alternatives)
            BinaryData data = blob.downloadContent();

            byte[] arr = data.toBytes();

            System.out.println("Blob size : " + arr.length);

            return ok(arr);
        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        try {
            BlobClient blob = containerClient.getBlobClient(path);

            // Download contents to BinaryData
            BinaryData data = blob.downloadContent();

            // Get the byte array
            byte[] arr = data.toBytes();

            // Log blob size
            System.out.println("Blob size : " + arr.length);

            // Pass the byte array to the sink
            sink.accept(arr);

            // Return a success result with no data (Void)
            return ok();

        } catch (Exception e) {
            e.printStackTrace();
            return error(INTERNAL_ERROR);
        }
    }

}
