const crypto = require('crypto');

module.exports = {
    createShort,
    processRegisterReply,
    prepareBlobUpload,
    prepareBlobDownload,
    preparelikePost,
    preparelikeget,
    prepareshortget
}

let bloburl;
let blobId;

// Function to create a short
async function createShort(requestParams, context, ee, next) {
    let userId = "jdoe";
    let pwd = "password123";

    requestParams.url = `/shorts/${userId}?pwd=${pwd}`;
}

function processRegisterReply(requestParams, response, context, ee, next) {
    if (typeof response.body !== 'undefined' && response.body.length > 0) {
        const responseBody = JSON.parse(response.body);

        if (responseBody.blobUrl) {
            bloburl = responseBody.blobUrl.split("/");
            blobId = bloburl[2].split("?")[0];
            bloburl = bloburl[1] + "/" + bloburl[2]
        }
    }

    return next();
}

async function prepareBlobUpload(requestParams, context, ee, next) {

    requestParams.url = `/${bloburl}`;

    const blobContent = crypto.randomBytes(100);
    requestParams.body = blobContent;
}

async function prepareBlobDownload(requestParams, context, ee, next) {

    requestParams.url = `/${bloburl}`;
}

async function preparelikePost(requestParams, context, ee, next) {

    requestParams.url = `/shorts/${blobId}/liskov/likes?pwd=54321`;

}

async function preparelikeget(requestParams, context, ee, next) {

    requestParams.url = `/shorts/${blobId}/likes?pwd=54321`;

}

async function prepareshortget(requestParams, context, ee, next) {

    requestParams.url = `/shorts/${blobId}`;

}

