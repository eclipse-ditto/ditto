# Ditto UI Documentation

## Configuration options

TODO TJ describe possible configuration options

### OpenID Connect configuration

The Ditto UI makes use of the [oidc-client-ts](https://authts.github.io/oidc-client-ts/) library and simply delegates
all configuration options to the [UserManager's constructor `settings`](https://authts.github.io/oidc-client-ts/classes/UserManager.html#constructor)'s 
being of type [UserManagerSettings](https://authts.github.io/oidc-client-ts/interfaces/UserManagerSettings.html), 
extending [OidcClientSettings](https://authts.github.io/oidc-client-ts/interfaces/OidcClientSettings.html).

Please refer to the [oidc-client-ts](https://authts.github.io/oidc-client-ts/) documentation in order to configure your
OIDC client accordingly.

## Development

To start development server use

`npm run start`

Browse to the UI

`http://localhost:8000/`

## Run unit tests

`npm run test`

## Build for production

`npm run build`
