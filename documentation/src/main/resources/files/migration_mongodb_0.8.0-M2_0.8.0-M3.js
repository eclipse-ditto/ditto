const policiesBasedSearchIndex = db.getCollection('policiesBasedSearchIndex');
policiesBasedSearchIndex.find().forEach(function (entry) {
    const id = entry._id;
    const splittedId = id.split(':');
    if (splittedId.length === 3) {
        const thingId = `${splittedId[0]}:${ splittedId[1]}`;

        policiesBasedSearchIndex.updateOne(
            {'_id': id},
            {$set: {'_thingId': thingId}}
        );
    } else {
        print(
            `Cannot migrate entry with id ${id}, as it seems to contain extra colons in its thingId, features or attributes.`);
    }
});
