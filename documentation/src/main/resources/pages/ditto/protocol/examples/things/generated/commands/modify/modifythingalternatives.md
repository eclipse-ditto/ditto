## Alternative ModifyThing commands

If you want to copy an existing Policy instead of creating a new one by yourself or reference an existing Policy, you
can adjust the ModifyThing command like demonstrated in the following examples.<br/>
This only works if a Thing with the given ``thingId`` does not exist, yet. If it exists the ``_copyPolicyFrom`` field
will be ignored.

### ModifyThing with copied Policy by Policy ID

If no Thing with ID ``org.eclipse.ditto:fancy-thing_53`` exists, this command will create a new Thing with ID ``org.eclipse.ditto:fancy-thing_53`` with a
Policy copied from the Policy with ID ``org.eclipse.ditto:the_policy_id_to_copy``.

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/",
  "value": {
    "thingId": "org.eclipse.ditto:fancy-thing_53",
    "policyId": "org.eclipse.ditto:the_policy_id",
    "_copyPolicyFrom": "com:acme:the_policy_id_to_copy"
  }
}
```

### ModifyThing with copied Policy by Thing reference

If no Thing with ID ``org.eclipse.ditto:fancy-thing_53`` exists, this command will create a new Thing with ID ``org.eclipse.ditto:fancy-thing_53`` with a
Policy copied from a Thing with ID ``org.eclipse.ditto:fancy-thing_52``.

```json
{
  "topic": "org.eclipse.ditto/fancy-thing/things/twin/commands/modify",
  "headers": {
    "correlation-id": "<command-correlation-id>"
  },
  "path": "/",
  "value": {
    "thingId": "org.eclipse.ditto:fancy-thing_53",
    "policyId": "org.eclipse.ditto:the_policy_id",
    "_copyPolicyFrom": "{% raw %}{{ ref:things/com:acme:fancy-thing_52/policyId }}{% endraw %}"
  }
}
```
