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

import tukano.api.Result;
import utils.Hash;
public class AzureBlobStorage implements BlobStorage{

    private final BlobContainerClient containerClient;

    private String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc242560247;AccountKey=CWYc6txoRyzIEUshC29sp1b7LwRtFLmeEkLN5Sfaxj0GORAE/PaNgaO5tk9Cb6kMfIseQ2Y8Alf++AStPeSdFQ==;EndpointSuffix=core.windows.net";
    private static final String BLOBS_CONTAINER_NAME = "images";

    public AzureBlobStorage() {
        containerClient = new BlobContainerClientBuilder()
                .connectionString(storageConnectionString)
                .containerName(BLOBS_CONTAINER_NAME)
                .buildClient();
    }

    @Override
    public Result<Void> write(String path, byte[] bytes) {
        if (path == null)
            return error(BAD_REQUEST);

        BlobClient blob = containerClient.getBlobClient(path);
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
        if (path == null)
            return error(BAD_REQUEST);

        BlobClient blob = containerClient.getBlobClient(path);
        if(!blob.exists() )
            return error(NOT_FOUND);

        var bytes = blob.downloadContent().toBytes();
        return bytes != null ? ok( bytes ) : error( INTERNAL_ERROR );
    }

    @Override
    public Result<Void> read(String path, Consumer<byte[]> sink) {
        if (path == null)
            return error(BAD_REQUEST);

        BlobClient blob = containerClient.getBlobClient(path);
        if(!blob.exists() )
            return error(NOT_FOUND);

        var bytes = blob.downloadContent().toBytes();

        sink.accept(bytes);
        return Result.ok();
    }

    @Override
    public Result<Void> delete(String path) {
        if (path == null)
            return error(BAD_REQUEST);

        BlobClient blob = containerClient.getBlobClient(path);
        if (blob.exists()) {
            blob.delete();
            return Result.ok();
        } else
            return error(NOT_FOUND);
    }
}