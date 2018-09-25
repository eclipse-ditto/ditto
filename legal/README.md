Generate/update the file [NOTICE-THIRD-PARTY.md](NOTICE-THIRD-PARTY.md) like this:

```bash
$ cd ../ # switch to ditto/ root dir
$ mvn generate-resources -Pgenerate-third-party-licenses
``` 

This will update the [NOTICE-THIRD-PARTY.md](NOTICE-THIRD-PARTY.md) according to the actually used dependencies 
including the license information.
