module.exports = {
    uiPort: process.env.PORT || 1880,

    mqttReconnectTime: 15000,
    serialReconnectTime: 15000,

    //socketReconnectTime: 10000,
    //socketTimeout: 120000,
    //httpRequestTimeout: 120000,

    debugMaxLength: 1000,
    debugUseColors: true,
    flowFile: 'flows-ditto.json',

    httpRoot: '/nodered',

    adminAuth: {
        type: "credentials",
        users: [{
            username: "admin",
            password: "$2a$08$H7n.UCeFdgxJUwemV6z9YOJXcvUcBHWDLLUBshm7RUOFxFWEbqkz2",
            permissions: "*"
        }],
        default: {
            permissions: "read"
        }
    },

    // Anything in this hash is globally available to all functions.
    // It is accessed as context.global.
    // eg:
    //    functionGlobalContext: { os:require('os') }
    // can be accessed in a function block as:
    //    context.global.os

    functionGlobalContext: {
        // os:require('os'),
        // octalbonescript:require('octalbonescript'),
        // jfive:require("johnny-five"),
        // j5board:require("johnny-five").Board({repl:false})
    },

    // Configure the logging output
    logging: {
        // Only console logging is currently supported
        console: {
            // Level of logging to be recorded. Options are:
            // fatal - only those errors which make the application unusable should be recorded
            // error - record errors which are deemed fatal for a particular request + fatal errors
            // warn - record problems which are non fatal + errors + fatal errors
            // info - record information about the general running of the application + warn + error + fatal errors
            // debug - record information which is more verbose than info + info + warn + error + fatal errors
            // trace - record very detailed logging + debug + info + warn + error + fatal errors
            // off - turn off all logging (doesn't affect metrics or audit)
            level: "info",
            // Whether or not to include metric events in the log output
            metrics: false,
            // Whether or not to include audit events in the log output
            audit: false
        }
    }
}
