module.exports = {
    updateUser
}

// Function to create a short
async function updateUser(requestParams, context, ee, next) {

    let username = "wales";
    let pwd = 12345;
    let email = "jimmy@wikipedia.com";
    let displayName = "";

    requestParams.url = `/users/${username}?pwd=${pwd}`;

    const user = {
        id: username,
        pwd: pwd,
        email: email,
        displayName: displayName
    };
    requestParams.body = JSON.stringify(user);
}

