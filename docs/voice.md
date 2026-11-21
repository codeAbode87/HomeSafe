# Asking the speakers

"Hey Google, how far away is Sarah?" on a Nest speaker, and the answer comes from the household's
own data: where Sarah's Tesla is, what's in savings, which workout is up today.

## How it works

Google doesn't let a Nest speaker hand a question to anyone else's server: Conversational Actions
were shut down in 2023, and Gemini for Home takes no third-party extensions ("Gemini built in" is for
hardware makers). The relay's Google Home link ([nest-cameras.md](nest-cameras.md) covers the camera
side; relay.py, "Google Home") can list devices, though, and a scene is a device. So:

1. The relay lists each question as a **scene** on the HomeSafe link: *Sarah's distance*,
   *Andrew's distance*, *Savings balance*, *Today's workout* (relay.py, "voice questions").
2. A **voice automation** in the Google Home app activates the scene when someone says the question
   ("How far away is Sarah?").
3. Google sends the relay `EXECUTE` `ActivateScene`. The relay replies at once, then works out the
   answer, has Gemini read it aloud (`VOICE_TTS_MODEL`, voice `VOICE_TTS_VOICE`), and plays it on
   `VOICE_SPEAKER` over Cast. The speaker fetches the audio from the relay on the home network
   (`/voice/audio/<token>`, kept 5 minutes). It takes a few seconds.

| Question | Scene | What it reads |
| --- | --- | --- |
| How far away is Sarah? | *Sarah's distance* | Tesla: asks once (at most once a minute, never waking the car), else the last fix the Cars page has; distance straight from home, driving or parked, the time if the fix is over 15 minutes old, and the arrival time from the car's navigation while it streams |
| How much is in savings? | *Savings balance* | the total of Plaid's depository accounts of subtype savings or money market, from the last bank sync, with how old it is if over a day; the accounts aren't named one by one |
| What's today's workout? | *Today's workout* | the training log on the relay of the login that linked Google Home (or `VOICE_FITNESS_OWNER`): whichever of chest, back and legs has waited longest, as the Fitness app's Today tab picks it; one under way or already done today comes first |

A person gets a distance question for each Tesla in `TESLA_CARS` named for its owner
(`sarahs_car` → Sarah). `VOICE_PEOPLE` names them otherwise: `{"Mom": "sarahs_car"}`.

## Setup (once)

1. **The speaker.** In `relay/voice.env` on the box (git-ignored):

   ```
   VOICE_SPEAKER=Kitchen Hub Max
   GEMINI_API_KEY=...
   ```

   `VOICE_SPEAKER` is the Cast name of a speaker, display or speaker group, as the Google Home
   app shows it. The relay's host networking lets it find the speakers by mDNS.

2. **The Gemini key.** Make one in Google AI Studio for this alone, restricted to the Generative
   Language API. It sits on the box, unlike the household's keys in the vault
   ([key-vault.md](key-vault.md)), which are sealed against the relay. Without a key, answers are
   spoken in Google Translate's voice, which needs none.

3. **Restart the relay** (`relay/deploy.sh`), then say *"Hey Google, sync my devices"*. The scenes
   appear under the HomeSafe link in the Google Home app.

4. **The automations.** Google Home app → Automations → Add → Household. Starter: *When I say to
   Google Assistant* → "How far away is Sarah" (add other wordings: "Where is Sarah"). Action:
   *Adjust home devices* → Scenes → *Sarah's distance*. One automation per question.

5. **Check it** without a speaker, on the box:

   ```bash
   docker exec homesafe-relay python relay.py voice-ask
   docker exec homesafe-relay python relay.py voice-ask voice.distance.sarahs_car
   docker exec homesafe-relay python relay.py voice-ask voice.workout andrew --speak
   ```

   The first lists the questions, the second prints an answer, and the third prints it and says it
   on `VOICE_SPEAKER`. A signed-in phone or browser can do the same: `GET /voice/questions`, and
   `POST /voice/ask` with `{"question": "voice.savings", "speak": false}`.

## Adding a question

A function that returns the sentence to say, `(user, now) -> str`, and a `VoiceQuestion` for it in
`voice_questions()`, with an id under `voice.` and the scene's name. Keep the wording in a pure
`voice_*_text` function and test it in `relay/test_relay.py` (`VoiceWordsTest`). After deploying,
"sync my devices" again and add its automation.

## What to know

- **One speaker answers.** Google doesn't tell the relay which speaker heard the question, so every
  answer is played on `VOICE_SPEAKER`. It interrupts anything playing there.
- **Anyone can ask.** A voice automation runs for any voice, guests' included, so a balance is said
  out loud to whoever is in the room. Leave *Savings balance* without an automation if that matters.
- **Google may say something first.** The Assistant can acknowledge the automation before the
  answer plays.
- **The answer's text isn't logged** (it can be a balance); `voice: … answered` is, with how long it took.
- **Distance is straight-line**, not by road.
