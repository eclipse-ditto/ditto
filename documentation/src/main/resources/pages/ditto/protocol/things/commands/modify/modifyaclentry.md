## ModifyAclEntry

```json
{
  "topic": "com.acme/xdk_53/things/twin/commands/modify",
  "headers": {},
  "path": "/acl/the_auth_subject",
  "value": {
    "__schemaVersion": 1,
    "the_auth_subject": {
      "READ": true,
      "WRITE": true,
      "ADMINISTRATE": true
    }
  }
}
```
