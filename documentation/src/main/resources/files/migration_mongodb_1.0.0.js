function insertDocuments(collection, documents) {
  const ids = [];
  const bulkWrites = [];

  documents.forEach(document => {
    const {_id} = document;

    ids.push(_id);
    bulkWrites.push({
      replaceOne: {
        filter: {_id},
        replacement: document,
        upsert: true
      }
    })
  });

  if (bulkWrites.length > 0) {
    collection.bulkWrite(bulkWrites, {ordered: false});
  }

  return ids;
}

function copyDocuments(sourceCollection, targetCollection, filter) {
  const sourceDocs = sourceCollection.find(filter);
  print(`Copy ${sourceDocs.count()} documents from ${sourceCollection} to ${targetCollection} ...`);
  insertDocuments(targetCollection, sourceDocs);
  print('Done!');
}

function migratePolicies() {
  const db = Mongo().getDB("policies");
  const targetJournal = db.getCollection('policies_journal');
  db.getCollectionNames()
    .filter(name => name.includes('policies_journal@'))
    .forEach(collectionName => {
      const sourceJournal = db.getCollection(collectionName);
      copyDocuments(sourceJournal, targetJournal);
    });

  const targetSnaps = db.getCollection('policies_snaps');
  db.getCollectionNames()
    .filter(name => name.includes('policies_snaps@'))
    .forEach(collectionName => {
      const sourceSnaps = db.getCollection(collectionName);
      copyDocuments(sourceSnaps, targetSnaps);
    });
}

function migrateThings() {
  const db = Mongo().getDB("things");
  const targetJournal = db.getCollection('things_journal');
  db.getCollectionNames()
    .filter(name => name.includes('things_journal@'))
    .forEach(collectionName => {
      const sourceJournal = db.getCollection(collectionName);
      copyDocuments(sourceJournal, targetJournal);
    });

  const targetSnaps = db.getCollection('things_snaps');
  db.getCollectionNames()
    .filter(name => name.includes('things_snaps@'))
    .forEach(collectionName => {
      const sourceSnaps = db.getCollection(collectionName);
      copyDocuments(sourceSnaps, targetSnaps);
    });
}

migratePolicies();
migrateThings();