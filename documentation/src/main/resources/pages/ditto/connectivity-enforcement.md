Messages received from external systems are mapped to Ditto internal format, either by applying some custom mapping or 
the default mapping for [Ditto Protocol](protocol-overview.html) messages. 

During this mapping the digital twin of the device is determined i.e. 
which Thing is accessed or modified as a result of the message. By default no sanity check is done if this target Thing 
corresponds to the device that originally sent the message. In some use case this might be valid, but in other scenarios 
you might want to enforce that a device only sends data to its digital twin. Note that this could also be achieved by 
assigning a specific policy to each device and use [placeholders](basic-connections.html#placeholders) in the 
authorization subject, but this can get 
cumbersome to maintain for a large number of devices.

With an enforcement you can use a single policy for all devices 
and still make sure that a device only modifies its associated digital twin. Enforcement is only feasible if the message
contains the verified identity of the sending device (e.g. in a message header). This verification has to be done by the
external system e.g. by properly authenticating the devices and providing the identity in the messages sent to Ditto.

The enforcement configuration consists of two fields:
* `input`: Defines where device identity is extracted.
* `filters`: Defines the filters that are matched against the input. At least one filter must match the input value, 
otherwise the message is rejected.

The following placeholders are available for the `filters` field:

| Placeholder    | Description  | Example   |
|-----------|-------|---------------|
| `{%raw%}{{ thing:id }}{%endraw%}` | Full ID composed of ''namespace'' + '':'' as a separator + ''name''  | eclipse.ditto:thing-42  |
| `{%raw%}{{ thing:namespace }}{%endraw%}` | Namespace (i.e. first part of an ID) | eclipse.ditto |
| `{%raw%}{{ thing:name }}{%endraw%}` | Name (i.e. second part of an ID ) | thing-42  |
