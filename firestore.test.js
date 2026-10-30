import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require('@firebase/rules-unit-testing');
const rulesRequire = createRequire(require.resolve('@firebase/rules-unit-testing'));
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  Timestamp,
  setLogLevel,
} = rulesRequire('@firebase/firestore');

const PROJECT_ID = 'demo-test';
let testEnv;

before(async () => {
  setLogLevel('error');
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync('firestore.rules', 'utf8'),
      host: '127.0.0.1',
      port: 8085,
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

const validTestResult = (userId = 'alice_uid') => ({
  userId,
  examType: 'TYT',
  subjectId: 'tyt_mat',
  subjectName: 'Matematik',
  topicId: 'temel_kavramlar',
  topicName: 'Temel Kavramlar',
  testId: 'odev_5',
  testTitle: 'Temel Kavramlar - Ödev 5',
  correctCount: 8,
  wrongCount: 1,
  emptyCount: 1,
  ungradedCount: 0,
  durationSeconds: 420,
  answersMapJson: '{"1":"A","2":"C"}',
  testSnapshotJson: '',
  createdAt: Timestamp.now(),
  updatedAt: Timestamp.now(),
});

const validCustomQuestion = (userId = 'alice_uid') => ({
  userId,
  examType: 'AYT',
  subjectId: 'ayt_mat',
  subjectName: 'Matematik',
  topicId: 'fonksiyonlar',
  topicName: 'Fonksiyonlar',
  difficulty: 'Zor',
  correctOption: 'D',
  questionText: 'Gerçel sayılar kümesinde tanımlı f ve g fonksiyonları için (fog)(x) = 3x + 4 olduğuna göre...',
  publisherTag: '3D Yayınları - PDF Kırpma',
  aiSolutionSummary: 'Bileşke fonksiyon özelliğinden f(g(x)) = 3x + 4 yazılır.',
  cropAspectRatio: 1.35,
  createdAt: Timestamp.now(),
  updatedAt: Timestamp.now(),
});

test('test_results: authenticated owner can create, read, update, and delete valid test result', async () => {
  const aliceDb = testEnv.authenticatedContext('alice_uid').firestore();
  const ref = doc(aliceDb, 'test_results/result_1');
  const payload = validTestResult('alice_uid');
  await assertSucceeds(setDoc(ref, payload));
  await assertSucceeds(getDoc(ref));
  await assertSucceeds(updateDoc(ref, { correctCount: 9, wrongCount: 0, updatedAt: Timestamp.now() }));
  await assertSucceeds(deleteDoc(ref));
});

test('test_results: rejects unauthenticated access and cross-user access', async () => {
  const aliceDb = testEnv.authenticatedContext('alice_uid').firestore();
  const bobDb = testEnv.authenticatedContext('bob_uid').firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const aliceRef = doc(aliceDb, 'test_results/result_alice');
  await assertSucceeds(setDoc(aliceRef, validTestResult('alice_uid')));

  await assertFails(getDoc(doc(unauthDb, 'test_results/result_alice')));
  await assertFails(setDoc(doc(unauthDb, 'test_results/result_unauth'), validTestResult('alice_uid')));

  const bobTarget = doc(bobDb, 'test_results/result_alice');
  await assertFails(getDoc(bobTarget));
  await assertFails(updateDoc(bobTarget, { correctCount: 10, updatedAt: Timestamp.now() }));
  await assertFails(deleteDoc(bobTarget));
});

test('test_results: schema rejects invalid examType or extra fields', async () => {
  const aliceDb = testEnv.authenticatedContext('alice_uid').firestore();
  const badRef = doc(aliceDb, 'test_results/bad_result');
  await assertFails(setDoc(badRef, { ...validTestResult('alice_uid'), examType: 'INVALID' }));
  await assertFails(setDoc(badRef, { ...validTestResult('alice_uid'), hackedField: true }));
});

test('custom_questions: owner can CRUD valid cropped question and cross-user is rejected', async () => {
  const aliceDb = testEnv.authenticatedContext('alice_uid').firestore();
  const bobDb = testEnv.authenticatedContext('bob_uid').firestore();
  const qRef = doc(aliceDb, 'custom_questions/q_custom_1');

  await assertSucceeds(setDoc(qRef, validCustomQuestion('alice_uid')));
  await assertSucceeds(getDoc(qRef));
  await assertFails(getDoc(doc(bobDb, 'custom_questions/q_custom_1')));
  await assertFails(deleteDoc(doc(bobDb, 'custom_questions/q_custom_1')));
  await assertSucceeds(deleteDoc(qRef));
});
