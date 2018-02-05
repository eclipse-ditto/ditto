## ModifyAcl

```json
{
  "topic": "com.acme/xdk_53/things/twin/commands/modify",
  "headers": {},
  "path": "/acl",
  "value": {
    "__schemaVersion": 1,
    "the_auth_subject_2": {
      "READ": true,
      "WRITE": false,
      "ADMINISTRATE": false
    },
    "the_auth_subject": {
      "READ": true,
      "WRITE": true,
      "ADMINISTRATE": true
    }
  }
}
```
