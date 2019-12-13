const THINGS_JOURNAL = 'things_journal';
const THINGS_SNAPS = 'things_snaps';
const POLICIES_JOURNAL = 'policies_journal';
const POLICIES_SNAPS = 'policies_snaps';

function reduceStep(key, values) {
  return values;
}

function finalizeStep(key, values) {
  let value = values;
  if (Array.isArray(values)) {
    if (values.length !== 1) {
      throw JSON.stringify(values);
    }
    value = values[0];
  }
  return value;
}

function checkOk(result) {
  printjson(result);
  if (result.ok !== 1) {
    throw JSON.stringify(result);
  }
}

/**
 * Copy all documents of source collection into target collection by map-reduce.
 * Due to the fixed output schema of map-reduce, the original document is under the field 'value'.
 * The target collection retains its previous documents.
 * Duplicate IDs abort the operation with an error.
 *
 * @param sourceCollection Name of the source collection.
 * @param targetCollection Name of the target collection.
 */
function copyDocuments(sourceCollection, targetCollection) {
  const sourceJournal = db.getCollection(sourceCollection);
  const targetExists = db.getCollection(targetCollection).count() !== 0;
  const out = targetExists ? { reduce: targetCollection } : targetCollection;
  print(`Copy ${sourceJournal.count()} documents from ${sourceCollection} to ${targetCollection} ...`);
  checkOk(db.runCommand({
    mapReduce: sourceCollection,
    map: function() { emit(this._id, this); },
    reduce: reduceStep,
    finalize: finalizeStep,
    out: out
  }));
}

function checkEmpty(collectionName) {
  const collection = db.getCollection(collectionName);
  if (collection.count() !== 0) {
    throw "Target collection " + collection + " is not empty!";
  }
  return collection;
}

/**
 * Prefix of collections to delete.
 *
 * @type {string}
 */
const TO_DELETE = 'z_delete_';

function renameToDelete(collection) {
  const c = db.getCollection(collection);
  checkOk(c.renameCollection(TO_DELETE + collection));
}

/**
 * Convert a journal collection from map-reduce format to event journal format.
 *
 * @param collection The journal collection name.
 */
function unmapJournal(collection) {
  db[collection].aggregate([
    {
      $project:{
        _id: 1,
        pid: '$value.pid',
        from: '$value.from',
        to: '$value.to',
        events: '$value.events',
        v: '$value.v',
        _tg: '$value._tg'
      }
    },
    {$out: collection}
  ]);
}

/**
 * Convert a snapshot store from map-reduce format to snapshot store format.
 *
 * @param collection The snapshot store collection name.
 */
function unmapSnaps(collection) {
  db[collection].aggregate([
    {
      $project:{
        _id: 1,
        pid: '$value.pid',
        sn: '$value.sn',
        ts: '$value.ts',
        s2: '$value.s2'
      }
    },
    {$out: collection}
  ]);
}

/**
 * Migrate journal and snapshot store for things or policies
 *
 * @param targetJournalName Name of the target journal collection---must be empty.
 * @param targetSnapsName Name of the target snapshot store collection---must be empty.
 */
function migrateThingsOrPolicies(targetJournalName, targetSnapsName) {
  const targetJournal = checkEmpty(targetJournalName);
  const targetSnaps = checkEmpty(targetSnapsName);
  db.getCollectionNames()
    .filter(name => name.includes(targetJournalName + '@'))
    .forEach(collectionName => {
      copyDocuments(collectionName, targetJournalName);
      renameToDelete(collectionName);
    });

  db.getCollectionNames()
    .filter(name => name.includes(targetSnapsName + '@'))
    .forEach(collectionName => {
      copyDocuments(collectionName, targetSnapsName);
      renameToDelete(collectionName);
    });

  print(`Unmapping ${targetJournal.count()} events ...`);
  unmapJournal(targetJournalName);
  print(`Unmapping ${targetSnaps.count()} snapshots ...`);
  unmapSnaps(targetSnapsName);
  print('Done.');
}

function migratePolicies() {
  migrateThingsOrPolicies(POLICIES_JOURNAL, POLICIES_SNAPS);
}

function migrateThings() {
  migrateThingsOrPolicies(THINGS_JOURNAL, THINGS_SNAPS);
}

function dropAllToDelete() {
  db.getCollectionNames()
    .filter(name => name.includes(TO_DELETE))
    .forEach(collectionName => db.getCollection(collectionName).drop());
}

function createJournalIndexes(collection) {
  checkOk(db[collection].createIndex(
    {
      pid: 1,
      from: 1,
      to: 1
    },
    {
      name: collection + '_index',
      background: true,
      unique: true
    }));
  checkOk(db[collection].createIndex(
    {
      pid: 1,
      to: -1
    },
    {
      name: 'max_sequence_sort',
      background: true
    }));
  checkOk(db[collection].createIndex(
    {
      _tg: 1
    },
    {
      name: 'journal_tag_index',
      background: true
    }));
}

function createSnapsIndexes(collection) {
  checkOk(db[collection].createIndex(
    {
      pid: 1,
      sn: -1,
      ts: -1
    },
    {
      name: collection + '_index',
      background: true
    }));
}

function createIndexes() {
  createJournalIndexes(THINGS_JOURNAL);
  createJournalIndexes(POLICIES_JOURNAL);
  createSnapsIndexes(THINGS_SNAPS);
  createSnapsIndexes(POLICIES_SNAPS);
}

/**
 * Migrate things and policies.
 * If any error aborts the migration, run 'revert()' to restore to previous state.
 */
function migrate() {
  migratePolicies();
  migrateThings();
  createIndexes();
  dropAllToDelete();
}

/**
 * Revert the migration.
 */
function revert() {
  db.getCollectionNames()
    .filter(name => name.includes(TO_DELETE))
    .forEach(collectionName => db.getCollection(collectionName)
      .renameCollection(collectionName.substring(TO_DELETE.length, collectionName.length)));
  db.getCollection(THINGS_JOURNAL).drop();
  db.getCollection(THINGS_SNAPS).drop();
  db.getCollection(POLICIES_JOURNAL).drop();
  db.getCollection(POLICIES_SNAPS).drop();
}

// Choose one of 'migrate()' or 'revert()'.
migrate();
// revert();
