# TubeNotes guide

TubeNotes is a small Android app. Share a YouTube video to it and pick one of three options:

| Option | What you get | Needs |
|---|---|---|
| **Transcript** | The video's full captions as readable paragraphs (switch on timestamps if you want them) | Nothing |
| **Summary** | One short paragraph: what the video is about and its main takeaway | A Claude API key |
| **Bullets** | 5–10 key points, most important first | A Claude API key |

Every video you open goes into the **Log** on the home screen, along with everything you made for it. Opening a
saved result again costs nothing: TubeNotes doesn't contact YouTube or Claude a second time.

## Install
1. On your phone, open the newest `TubeNotes_*.apk` from the [`apk/`](../apk/) folder or from the repo's
   **Releases** page (tagged `tubenotes-…`).
2. Open the downloaded file and allow "install unknown apps" for your browser if Android asks.
3. Later versions install over the old one and keep your log.

## One-time setup (summaries and bullet points)
1. Go to **console.anthropic.com → API keys** and create a key (it starts with `sk-ant-`).
2. In TubeNotes, tap the **⚙ Settings** icon, paste the key and tap **Save key**.

The key is stored only on your phone. Each summary or bullet list is billed to your Anthropic account, usually a
few cents for a typical video (the model is Claude Opus 5.5 at low effort). Transcripts are free.

## Everyday use
- **From YouTube:** open a video → **Share** → **TubeNotes**. (If you don't see it, tap **More** in the share sheet.)
- **By pasting:** copy a link, open TubeNotes, paste it into **YouTube link** and tap **Open video**.
- Pick **Transcript**, **Summary** or **Bullets**. Each result has **Copy**, **Share** and (for Claude's results)
  **Redo** for a fresh version.
- **Log:** tap any row to reopen it. A search box appears once you have more than 5 videos. The trash icon on a
  video's page deletes it.
- **Export:** Settings → **Export as text** (readable) or **Export as JSON** (full backup), then send it to Drive,
  email or any app. Exports never include your API key. Cloud backup is off, so export now and then if the log
  matters to you.

## When something goes wrong
| Message | What to do |
|---|---|
| *YouTube didn't share this video's captions…* | 1) Tap **Try again** a bit later. 2) Tap **Paste transcript**: in YouTube, open the video's description → **Show transcript**, select all the text, copy, paste it into TubeNotes and tap **Use this**. 3) Try on Wi-Fi instead of mobile data, or the other way round. |
| *This video has no captions* | The uploader turned captions off and YouTube has none. Use **Paste transcript** if you have the text from somewhere else. |
| *Add your Claude API key…* / *rejected the API key* | Settings → paste a valid key → **Save key**, then tap **Try again**. |
| *rate limit* / *overloaded* | Wait a minute and tap **Try again**. |
| *Network error* | Check your connection and tap **Try again**. |

## For developers
- Code lives in [`tubenotes/`](../tubenotes/). It's a second module in the same Gradle build as Spotter.
- Build and test: `./gradlew :tubenotes:testDebugUnitTest :tubenotes:assembleDebug`
- CI: `.github/workflows/build-tubenotes.yml` builds and publishes a release on pushes to `main` or this branch.
- Transcripts come from YouTube's caption tracks (the same request the YouTube Android app makes). Summaries and
  bullets use the Claude Messages API (`claude-opus-5-5`, effort `low`, `fallbacks: "default"`).
