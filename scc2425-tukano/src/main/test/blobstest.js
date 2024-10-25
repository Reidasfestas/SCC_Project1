const crypto = require('crypto');

module.exports = {
    createShort,
    processRegisterReply,
    prepareBlobUpload
}

let token;
let bloburl;

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
            bloburl = bloburl[1] + "/" + bloburl[2]
        }
    }

    return next();
}

async function prepareBlobUpload(requestParams, context, ee, next) {

    requestParams.url = `/${bloburl}`;

    const blobContent = crypto.randomBytes(100);
    requestParams.body = blobContent;

    // Log for debugging
    console.log('Uploading blob with blobId:', bloburl);
    console.log('Blob content:', requestParams.body.toString());
}

