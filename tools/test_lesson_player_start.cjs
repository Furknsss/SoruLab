// Exercise the JavaScript shipped in the Kotlin WebView HTML; no network/API key.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync(process.argv[2] || 'app/src/main/java/com/example/ui/screens/SmartTopicLectureDialog.kt', 'utf8');
const html = source.slice(source.indexOf('internal fun buildYoutubeEmbedHtml'));
const script = html.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
function fixture(start, acceptsSeek = true) {
  let options, current = 0, state = 1, paused = false;
  const seeks = [], messages = [], timers = [];
  const player = {
    seekTo(second, allowAhead) { seeks.push([second, allowAhead]); if (acceptsSeek) current = second; },
    playVideo() {}, pauseVideo() { paused = true; state = 2; },
    getCurrentTime() { return current; }, getPlayerState() { return state; }
  };
  const context = { console: { log: x => messages.push(x) }, setInterval: cb => timers.push(cb),
    YT: { PlayerState: { PLAYING: 1 }, Player: function(id, settings) { options = settings; return player; } } };
  vm.createContext(context);
  vm.runInContext(script.replaceAll('$startSeconds', String(start)).replaceAll('$videoId', '35lqUJGwj3Y')
    .replaceAll('$endBoundary', '10477').replaceAll('$end', ',end:10477'), context);
  context.onYouTubeIframeAPIReady();
  options.events.onReady({ target: player });
  return { options, seeks, messages, tick: () => timers.forEach(cb => cb()),
    playing: () => options.events.onStateChange({data:1}), setTime: x => { current=x; },
    paused: () => paused };
}
const exact = fixture(8407);
assert.equal(exact.options.playerVars.start, 8407);
assert.deepEqual(exact.seeks, [[8407, true]]);
exact.playing(); exact.tick();
assert.ok(exact.messages.includes('SORULAB_TIME:8407'));
exact.setTime(100); exact.playing(); exact.tick();
assert.equal(exact.seeks.length, 1, 'User rewind after confirmed startup stays possible');

const broken = fixture(8407, false);
broken.playing(); broken.tick(); broken.tick(); broken.tick();
assert.equal(broken.seeks.length, 3, 'Seek retries are bounded');
assert.ok(broken.messages.includes('SORULAB_ERROR:POSITION'));
assert.ok(broken.paused());
assert.ok(!broken.messages.some(x => x.startsWith('SORULAB_TIME:')), 'Never save false chapter progress');

const wrongPosition = fixture(8407, false);
wrongPosition.setTime(10000); wrongPosition.playing();
assert.equal(wrongPosition.seeks.length, 2, 'Wrong later chapter also requires correction');
assert.ok(!wrongPosition.messages.some(x => x.startsWith('SORULAB_POSITION_CONFIRMED:')));

const resumed = fixture(8500);
resumed.playing(); resumed.tick();
assert.deepEqual(resumed.seeks, [[8500, true]]);
assert.ok(resumed.messages.includes('SORULAB_TIME:8500'));
const finished = fixture(8407); finished.playing(); finished.setTime(10477); finished.tick();
assert.ok(finished.paused(), 'Chapter end survives the explicit YouTube seek call');
const beginning = fixture(0); beginning.playing(); beginning.tick();
assert.ok(beginning.messages.includes('SORULAB_TIME:0'));
console.log('Player startup checks passed: exact chapter, retry/error, wrong chapter, rewind, resume, zero start.');
