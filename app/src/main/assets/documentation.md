# FukeX Documentation

FukeX is a lightweight, easy-to-use Android media player written in Kotlin. It features a simple UI and was built specifically with screen readers in mind. We created FukeX because there were no good media players on the Android market that were both feature-rich and fully accessible. We are screen reader users ourselves, so we've kept accessibility front and center, ensuring every step is easily navigable with tools like TalkBack.

## What is special about FukeX?

We offer many features that other media players lack. Some of our favorites include:

* Up to 25-dB volume boost amplifier: Though I personally recommend maxing it out at 20dB, because if you increase it to 25dB, the sound might crack on some devices.

* Crossfading and gapless playback: Have a party playlist and want that DJ feel? This feature is for you! You can even set a different fade time for each thing that can interrupt a song.

* In-app playlist locking: Use this feature to hide your playlist with a PIN, a password, or your fingerprint. Note that it only hides the playlist within the app, not on the system level.

* Multi-playlist support: Want to switch between multiple playlists? Yeah, you can do that too! Our tabbed interface allows you to switch between playlists effortlessly.

* SMB share: The killer feature! Can you imagine? You don't need to keep all your songs on your phone. Just connect to an SMB server (like Tailscale on your home server or PC), and you can play songs directly from your PC to your phone via the media player!

* Import and export playlists: Move a playlist to another phone, or keep a backup, using a small JSON file.

* Resume where you left off: FukeX remembers the track and the position you stopped at, for every playlist separately.

* Full media notification: Play, pause, previous and next from the notification shade and the lock screen, without opening the app.

* Music library: Browse every song on your phone by song, artist, album or genre, and play it without adding it to a playlist first.

* Up Next queue: Play a track next, or add it to the end of the queue, without touching your playlist.

* 10-band equalizer: With presets, a preamp, and separate settings for your speaker, wired headphones, Bluetooth and USB audio.

* Synced lyrics: Read along on the lyrics screen, or have TalkBack read each line as it is sung.

* Voice assistant: Press and hold Volume Down and tell FukeX what to play.

* Experimental YouTube support: Search YouTube and play a result.

* Android Auto: Browse and play your playlists from the car screen.

* Optional visual icon pack: A polished set of icons for every screen, menu and dialog, downloaded only if you want it.

## What files can FukeX play?

FukeX plays these file types, both from your device and from an SMB share:

mp3, mp4, m4a, aac, flac, wav, ogg, opus, alac, mkv, and webm.

Video files are treated as audio only. In the file picker, playable media files are marked with an audio icon, while other files show a generic file icon and do nothing if tapped. When you use "Add entire folder", only playable media files are added.

You need Android 8.0 or newer to run FukeX.

## How to get it?

You can download FukeX to your device from our website:

Click here to download FukeX

## Updating FukeX

Each time FukeX opens it quietly checks GitHub for a newer release. You can also check yourself any time: open settings, scroll to About, and choose "Check for updates".

When a newer version exists, FukeX tells you what is new and asks "Update now?". If you say yes:

1. FukeX downloads the update and shows the progress. It then checks that the file is complete, that it really is FukeX, that it is newer than your version, and that it is signed by the same key as the FukeX you have.
2. The first time, Android needs your permission for FukeX to install apps. FukeX explains this and opens the right Android page. Switch on "Allow from this source", then press back to return to FukeX.
3. Android's own installer opens and asks if you want to update FukeX. Confirm, and it installs over the old version. Your playlists, settings and saved positions are kept.

Versions are compared as numbers, so 0.10.0 counts as newer than 0.9.0, and a pre-release such as 1.0.0-rc.1 counts as older than 1.0.0. If the update is not newer, or is signed with a different key, FukeX tells you and does not install it.

If you would rather not update from inside the app, you can still download the APK from the release page and install it yourself, as described below.

## Installing the APK file

Once you obtain the APK file, here is how you install it:

1. Navigate to your file manager and choose the downloaded APK. Alternatively, if you are still on the Chrome page where you downloaded the APK, tap "Customize and control Google Chrome" (or "More options" if using another browser), then select "Downloads", and directly access the APK file.

2. Installing: When you tap the APK file, a system restriction screen for "Unknown apps" installation will pop up. Click the "Settings" button and toggle the switch that says "Allow this installation". If you can't find the page to allow unknown app installations, go to your phone settings and search for "Unknown apps" or similar. After that, choose the app you plan to install FukeX with, like a browser or file manager.

3. Confirm the installation: Once you're done with that hassle, tap "Install" to install the app. If you receive a Google Play scan warning, just let it do its job by tapping "Scan". If you wanna be quick, use the "Install without scanning" TalkBack global accessibility action.

Congrats! You've successfully installed FukeX.

## Permissions FukeX asks for

The first time you open FukeX, it will ask for a few things. Here is what each one is for, so you're not left guessing:

* Notifications: So FukeX can show the playback notification with the play, pause, previous and next buttons. Say no and playback still works, you just lose those controls.

* All files access: FukeX uses its own file picker instead of the system one, because the system picker is painful with a screen reader. To browse your storage that way, Android needs this permission. FukeX explains this in a dialog first, and you can choose "Open settings" to grant it or "Not now" to skip. If you skip it, everything else still works and you can still play from an SMB share.

* Music and audio access: Lets the music library show the songs on your phone. Say no and the rest of the app still works.

* Microphone: Only used by the voice assistant, and only for the few seconds after you trigger it. FukeX asks for it when it first opens, but you can say no if you never plan to use the assistant.

* Internet: Used to reach your SMB server, to look up lyrics if you allow it, to search YouTube, to download the optional icon pack, and by your phone's speech recognition service for the voice assistant. FukeX does not phone home, and there are no ads or trackers.

* Install unknown apps: Only asked for when you update FukeX from inside the app, and only the first time. See the updating section.

* Vibration: A short buzz tells you the voice assistant is starting to listen.

## Creating your first playlist

Wanna start listening to your favorite songs? Follow these steps:

1. Open the app, FukeX. Obviously, where else will you play the media, in the air?

2. You will see the following options:

* Music Library: Opens the built-in music library to browse songs on your device.

* Import playlist: Loads a playlist you previously exported.

* Settings: This is used to go to the settings page.

* Default playlist tab, selected: This is a default playlist, which is currently empty. You can create a new playlist, or add media to play directly here.

* Seekbar / now playing indicator bars.

* Music controls: Like play/pause, previous, next, more options, and finally, the floating button at the bottom right which will be useful here, "Add media to playlist".

Right here, we'll be adding a folder or song to the default playlist. Start by selecting the "Add media to playlist" button. It will present you with a dialog titled "Add Media", asking "Where would you like to add media from?"

You'll see the following options:

* Music Library: Browse songs already indexed on your phone by song, artist, album or genre.

* Local Device: Choose this option if you want to browse your device's folders directly. For now, we'll choose this option.

* SMB Share: You can play songs from your home servers or your PC. Keep reading the documentation or jump to the heading if you wanna learn how to connect it.

* YouTube Search: Search YouTube and add a result to your playlist (experimental).

Finally, when choosing "Local device", it will display a custom file picker where TalkBack accessibility gestures will work. Browse for the file you want to play, or if you want to select an entire folder, keep focus on the folder you want to add, and swipe with one finger to reveal the TalkBack action saying "Add entire folder". As soon as you choose this, your playlist will be saved in a JSON file just in case, and your data stays only on your device.

A couple of things worth knowing about the file picker:

* "Add entire folder" also grabs every subfolder inside it, so pointing it at your whole Music folder works fine.

* Big folders take a moment. You'll hear "Scanning folder for media files" while it works.

* The back gesture moves you up one folder. Press back at the top level and the picker closes.

* Media is always added to the playlist tab you currently have open, and it goes to the end of the list.

As soon as you add the folder, its playable tracks appear in your playlist. Tap any track in the list or press the Play button to start listening. Now let's explore the rest of the player.

## Around the player screen

Once a playlist has media in it, the screen is split into two parts.

The top part is the track list. Every track is a row you can tap to play. The one currently playing is marked with a play icon and is spoken as "Playing".

The bottom part is the playback bar, and it holds:

* Now playing line: Tells you the playlist name, and which track number you are on out of how many.

* Seek slider: Drag it, or use the TalkBack up and down swipe, to move within the song. It reads out as "3:12 of 4:05".

* Previous and Next buttons: These are dimmed at the very start and the very end of a playlist, so you always know where you are.

* Play / Pause button.

* More options: Search within playlist, Playlist info, Up next, Lyrics, Equalizer, amplifier toggle, Playback Effects, and the hide and remove actions for this playlist.

FukeX saves your position roughly every five seconds and again whenever you leave a playlist. Come back later and it picks up the same track, at the same second.

## Reordering, queuing, and removing tracks

Keep focus on any track in the list and swipe up or down with one finger (or tap its Track options button) to reveal its actions:

* Play next: Plays the track right after the one currently playing, without changing your playlist.

* Add to queue: Adds the track to the end of the Up Next queue.

* Move Up: Swaps the track with the one above it.

* Move Down: Swaps it with the one below.

* Remove: Takes the track out of the playlist. This never deletes the file from your storage, it only removes it from the list.

If you remove the track that is playing, FukeX simply moves on to the next one, or to the new last track if you removed the one at the bottom. Playback only stops if you removed the very last track the playlist had.

## Searching through a playlist

Searching through a playlist is straightforward. Go to the playlist, and near the playback controls, you will see a 3-dots menu, or the "More options" button for TalkBack. You can either use the accessibility action or tap the button and select the "Search" option.

After that, a text box will open up. Type the name of the song, and the result will pop up right next to the text box. Tap any result and it starts playing straight away, and the search closes itself.

The search looks at track names and ignores capital letters, so typing "night" finds "Night Drive" just fine. Track names come from the file name with the extension trimmed off, which is why you see "Night Drive" rather than "Night Drive.mp3".

## Playlist info

Also in the 3-dots menu is "Playlist info". It gives you a quick summary:

* Name of the playlist.

* Total tracks it contains.

* Total size on disk of all the tracks added together. Tracks that live on an SMB share are not measured, since that would mean asking the server about every single file. If any tracks were skipped, FukeX tells you how many.

* Whether the playlist is locked.

Handy when you want to know whether that podcast dump is eating your storage.

## Creating a new playlist

Oh, so let's jump into creating your own playlists.

Keep your focus on the default playlist tab, and using the TalkBack action, find the "Create new playlist" button. As soon as you activate it, a text box will pop up, asking you for the name of the playlist. You can name it anything. After providing a name, press "Create". When you're done with the creation of the playlist, make sure to add a media file! ROFL.

The new playlist becomes the selected tab right away, so the very next thing you add lands in it.

## Managing your playlist tabs

Keep focus on a playlist tab and swipe up or down with one finger, or tap the 3-dots button sitting inside the tab, to get to these:

* Create new playlist: The same thing described above.

* Move Left and Move Right: Rearrange the order of your tabs. Put the playlist you use daily first, and stop swiping past four others every time.

* Hide: Locks the playlist behind a PIN or password. More on this below.

* Unlock: Only shows up on a playlist that is already locked.

* Export: Saves the playlist to a JSON file.

* Remove: Deletes the playlist itself. Again, your actual audio files are untouched.

One small rule to keep in mind: while you only have a single playlist, moving, hiding and removing are not offered. FukeX always keeps at least one playlist around, otherwise you would have nowhere to add media.

## Importing and exporting playlists

An exported playlist is a tiny JSON file holding the playlist name and the location of every track. It does not contain the songs themselves, so it stays small, and it is easy to keep in your cloud storage as a backup.

To export: use the "Export" action on a playlist tab, or "Export Playlist" from the tab's 3-dots dialog. Android will ask you where to save it and suggest the playlist name as the file name.

To import: press the "Import Playlist" button in the top bar, and pick the JSON file. It always comes in as a brand new playlist, so importing twice gives you two copies rather than overwriting anything.

Two honest warnings.

Because the file stores locations and not the audio itself, an exported playlist only works on a phone that can reach those same locations. Moving a playlist of local files to a different phone will give you a list of tracks that refuse to play. Playlists made of SMB tracks travel much better, as long as the new phone can reach the same server.

Exported playlists never contain your SMB username or password. That's good for sharing, but it does mean that after importing an SMB playlist on a new phone, you need to connect to that server once through "Add media to playlist" so FukeX learns the login. After that the imported tracks play normally.

## Locking a playlist

Got a playlist you'd rather other people didn't stumble across? Use "Hide".

1. Pick "Hide" from the tab's actions or from the 3-dots menu in the playback bar.

2. Choose the type. "PIN" gives you a number keypad, "Password" gives you the full keyboard. Both need at least 4 characters.

3. Type it in and press "Hide".

The playlist tab disappears from the row of tabs immediately, and a lock button appears in the top bar so you can get back to it. That lock button is only there while you actually have something locked.

Please read this bit twice: if you forget your PIN or password, FukeX cannot get the playlist back for you. There is no reset, no email, no recovery question.

Also worth saying plainly, since we'd rather be honest than sound impressive: this hides a playlist inside FukeX. It is not encryption. Your audio files stay exactly where they were on your storage, and any file manager can still see them. What FukeX does guarantee is that your PIN is never written down as you typed it, so someone reading FukeX's data files cannot simply read your PIN back out.

## Getting back into a locked playlist

Press the lock button in the top bar. You'll get a list of every locked playlist. Choose one, and then either:

* Your fingerprint appears, if you turned on biometric unlock in settings. Your device PIN or pattern works here too, so a failed fingerprint isn't the end of the road, and if you dismiss it FukeX just falls back to asking for the playlist PIN.

* Or the PIN box appears, and you type what you set earlier.

Either way, the playlist comes back as a normal tab and you can play it as usual. This is a temporary visit, though. FukeX relocks it based on your lock timeout setting.

If you want the playlist to stop being locked altogether, use the "Unlock" action on its tab. It asks for the PIN or password one more time, and then the lock is removed for good.

## Lock timeout

In settings, "Lock Timeout" decides how soon a temporarily unlocked playlist goes back into hiding. Your choices:

* Immediate: Relocks the moment you leave the app.

* 1 Minute, 2 Minutes or 5 Minutes: Relocks after that much time without you touching the playlist.

* When Screen Locks: Relocks when your screen turns off.

There's one deliberate exception. A playlist that is actively playing does not relock underneath you on a timer, because having the music cut out mid-song would be a rubbish way to protect anything.

## Connecting to an SMB share

This is the feature we're proudest of. Your music can live on your PC, your NAS, or a home server, and FukeX streams it.

1. Press "Add media to playlist" and choose "SMB Share".

2. Fill in the connection screen:

* SMB URL: Something like smb://192.168.1.100/Music/. The smb:// part is already filled in for you.

* Username: Leave it empty for a guest or public share. If your server requires a domain, enter it as `DOMAIN;username`.

* Password: Same, leave it empty if the share doesn't need one.

3. Press "Connect".

From there it behaves exactly like the local file picker. Browse into folders by tapping them, go back up with the back gesture or the "Go up" button, tap a track to add it, or use the "Add entire folder" TalkBack action to pull in a whole folder including everything nested inside it.

Some practical notes:

* Over a home Wi-Fi network this is smooth. Over a slower link, give the first few seconds of a track time to buffer.

* FukeX keeps a cache of recently streamed audio, up to 500 MB, so replaying something you just heard doesn't hit the network again. Clearing the app's cache in Android settings empties it.

* If the connection details are wrong you'll get a clear "Failed to access SMB" message rather than a silent empty folder. If the server simply isn't answering, FukeX gives up after a few seconds instead of hanging forever.

* Your username and password are saved separately from your playlists, once per server. They are never written into a playlist file, so exporting an SMB playlist does not hand your password to anyone you send it to.

## The music library

Tap the Music Library button on the main screen to see every song on your phone, read from Android's own media database. FukeX asks for permission to read your music the first time you open it.

Use the buttons at the top to group the list by Songs, Artists, Albums or Genres, or type in the search field to find a song, artist or album. On a group, "Play all" starts playing it from the top and "Add all to playlist" copies it into a playlist of your choosing.

On a single song, open "Song options" for Play next, Add to queue, and Add to playlist. Playing a song straight from the library never changes any of your playlists.

## Up Next and the queue

Every track on a playlist or in the library has "Play next" and "Add to queue" in its options. "Play next" puts the track right after the one that is playing, and "Add to queue" puts it after everything you already queued.

Queued tracks are never copied into your playlist, and they do not move the saved position of the playlist. Open "Up next" from the player's More options menu to see what is coming. It marks the track that is playing as "Now playing" and your additions as "Queued". You can tap any track in Up Next to jump straight to it, or take a queued track out with "Remove from queue".

## The equalizer

Open the 3-dots menu in the playback bar and choose "Equalizer".

* Enable Equalizer: Turns the equalizer on or off.

* Preset: Flat, Bass Boost, Treble Boost, Vocal, Rock, Pop, Jazz, Classical, Electronic, Hip-Hop, Loudness or Spoken Word. Moving a band yourself switches the preset to Custom.

* Ten bands, from 31 Hz up to 16 kHz, each adjustable by up to 12 dB either way. Swipe up or down on a band to change it, and the slider reads out its frequency and gain.

* Preamp: Lowers or raises the level before the bands, so a big boost does not clip. If a preset sounds distorted, pull the preamp down a few dB.

* Reset to flat: Puts every band back to zero.

* Editing: Each output keeps its own settings, for the phone speaker, wired headphones, Bluetooth, and USB audio. The "Editing" menu picks which one you are changing. FukeX switches to the matching settings by itself when you plug in or connect something, so headphones can sound different from the speaker.

## Lyrics

Open the 3-dots menu and choose "Lyrics". FukeX looks for lyrics in this order: a copy it saved earlier, a .lrc file with the same name sitting next to the song, and finally the lrclib.net website.

The lyrics screen shows the whole song, and "Current line" marks the line being sung when the lyrics are synced. If a song has no lyrics, the screen says so.

Two settings control this, under Lyrics in the settings screen:

* Read Lyrics Aloud: TalkBack reads each synced line as it plays. Off by default, since it talks over your music.

* Find Lyrics Online: When there is no .lrc file, FukeX sends the song's title and artist to lrclib.net to look them up. Turn it off if you would rather nothing leave your phone.

Lyrics that were found are saved on your phone, so each song is looked up only once.

## Adding songs from YouTube (experimental)

In "Add media to playlist", choose the YouTube option, type what you are looking for and press Search. Choose a result to play it. YouTube tracks are streamed, so they need an internet connection, and since this feature is experimental it may stop working whenever YouTube changes something on its side.

## The voice assistant

The voice assistant lets you control FukeX without touching the screen.

To turn it on, open settings, find Voice Assistant, and switch on "Enable Voice Assistant". Allow the microphone permission if you haven't yet. Then:

1. Press and hold Volume Down for about half a second. You will feel a short buzz and hear "Listening". A quick press still just lowers the volume. If you switched on "Shake to Wake Assistant", shaking your phone twice works too.
2. Wait for the beep, then say a command.
3. FukeX tells you what it is doing, or what it did not understand.

While you talk, the music is turned down so it does not drown you out. Holding Volume Down is ignored during phone or VoIP calls so it never gets in the way of call volume. Switch on "Work in Background" to use the assistant while FukeX is minimized. For holding Volume Down to work in the background, also turn on "FukeX voice assistant shortcut" in your phone's accessibility settings (the "Volume Key Shortcut" item in FukeX settings takes you there). Android does not let apps see the volume keys in the background any other way. The shortcut only watches the Volume Down key and does not read your screen.

Speech recognition uses on-device recognition on Android 13 and newer when available, and otherwise falls back to your phone's speech service, which may need an internet connection depending on your device. If the assistant says it is not available, check that a speech recognition app, like Google, is installed and up to date.

What you can say:

* Play something: "Play Shape of You", or "Tum Hi Ho bajao". FukeX looks in your playlists first, then in your music library, and only then on YouTube. If you name an artist or an album, it plays that whole artist or album.

* Switch results or versions: If multiple versions of a song exist in a playlist, or when searching on YouTube, say "Next result", "Another version", "Different one", "Dusra bajao", or "Previous result" / "Last result" / "Pichla result" to step through them without searching again. You can also jump directly by number or ordinal (1 to 20), such as "Option 2", "Result 3", or "Play second result".

* Play it later: "Play Believer next" or "Add Believer to queue".

* Control playback: "Pause", "Stop", "Resume", "Play", "Next", "Previous", and "Start over" to restart the current track. When you are at the end or beginning of a queue with an active voice search, "Next" and "Previous" also step to the next or previous search result.

* Seek: "Forward", "Rewind", "Forward 30 seconds", "Go back 1 minute". With no number, it moves 10 seconds.

* Volume: "Volume up", "Volume down", "Louder", "Quieter". This changes your phone's media volume.

* Ask what is playing: "What's playing" or "What song is this".

* Amplifier: "Amplifier on", "Amplifier off", "Amplifier increase" (capped at 15 dB by voice for safety; higher levels up to 25 dB can be set in Settings), "Amplifier decrease".

* Help: "What can I say" lists the commands out loud, and "Open documentation" brings up this page.

A few commands also work in Hindi and Spanish, for example "bajao", "agla", "pichla", "siguiente" and "pausa".

Song names that contain a command word are fine. "Play Back in Black" plays the song instead of going back, because "play" at the start of a sentence always wins.

If the song is in a locked playlist that is not currently unlocked, the assistant tells you so rather than playing it. Unlock the playlist first and ask again. It also will not read out the name of a track from a locked playlist when you ask what is playing.

If the assistant keeps missing you, speak a little slower and closer to the phone, in a quieter place. It tries the several alternatives the recognizer offers, so it forgives a mistake or two.

## Visual icons

FukeX works fully with its standard icons. If you would like a more polished look, it can download an icon pack that restyles the icons on every screen, menu and dialog, and adds small icons beside the headings in settings. The pack is not built into the app. FukeX asks you about it the first time it opens, and downloads it from the FukeX page on GitHub only if you say yes.

You can manage it any time in settings, under Visual Icons:

* Download Visual Icons: Fetches the pack. It is tiny, about 16 KB.

* Use Visual Icons: Switches the pack on or off without deleting it.

* Check for icon updates: Looks for a newer version and installs it.

* Delete icon pack: Removes the pack and goes back to the standard icons.

The pack is checked against a checksum before it is used, and a download that does not match is thrown away. The icons only change how things look. Button names, labels and everything TalkBack announces stay exactly the same with or without the pack.

## Android Auto

FukeX shows up in Android Auto and in other places that browse media, such as Wear OS. You can browse your playlists and pick a track from the car screen. Locked playlists are never shown there, and only trusted apps from Google and the system are allowed to browse your playlists.

## Controlling playback outside the app

While something is playing, FukeX puts a notification in your shade with previous, play or pause, and next. It also appears on your lock screen, and the same controls work from your car, your smartwatch, and anywhere else Android surfaces media controls.

Playback keeps going while you switch between tabs or open the library, the equalizer or the settings, and after you close the app, until you pause it or close the playlist.

The play, pause and skip buttons on wired and Bluetooth headsets work too, including the double-press for next track that most earbuds use.

The notification sticks around while paused so you can start playing again, and clears itself when you close the playlist.

Tapping the notification body brings you straight back into FukeX.

## Sharing the sound with other apps

FukeX plays nicely with whatever else is making noise on your phone:

* A phone call, or another media app taking over, pauses FukeX. When a call ends, FukeX picks up again on its own.

* Something short, like a navigation instruction or a TalkBack announcement over the media stream, quietly drops FukeX's volume for a moment instead of stopping it, then brings it back up.

* Unplugging your headphones pauses playback rather than blasting the song out of the phone speaker.

## The volume amplifier

Some recordings are just quiet. The amplifier pushes them louder than Android normally allows.

Set the level in settings, from 0 up to 25 dB in 5 dB steps. Then switch it on or off whenever you like from the 3-dots menu in the playback bar, without going back to settings, since the menu item tells you the current level, for example "Enable Amplifier (Boost 10dB)".

Above 15 dB, FukeX shows a warning before applying it. That warning is not decoration. Pushing a small phone speaker that hard for a long time can genuinely damage it, and heavily boosted audio tends to distort. 20 dB is our sweet spot, 25 dB is there for the brave.

## Playback effects: speed, pitch and bass boost

Sometimes you want a track a little faster, a lecture a little slower, or just more thump in your headphones. Open the 3-dots menu in the playback bar and choose "Playback Effects" for three sliders in one dialog:

* Playback Speed: From 0.5x up to 2x. Slow down a podcast to catch every word, or speed through an audiobook you've already heard a hundred times.

* Playback Pitch: Also 0.5x up to 2x, and it's independent of speed, so you can change one without dragging the other along with it.

* Bass Boost: Off by default, up to a level of 1000 in steps of 100. Turn it up when your headphones just aren't giving you the low end you want.

All three take effect immediately and are remembered the next time you open FukeX, so you only have to set them once.

## Evening out the volume between tracks

Ever get blasted by one song right after a quiet one? That's what ReplayGain is for. Turn on "Enable ReplayGain" in settings, under Playback, and FukeX will read the volume tag that most music library tools already write into your files, then adjust each track to a consistent loudness on the fly.

It's off by default, since not every file has the tag, but if you've got a mixed playlist and keep reaching for the volume button, switch it on and forget about it. Tracks without the tag just play at their normal volume.

## Fading and gapless playback

This is what gives FukeX that DJ feel. Rather than one blunt crossfade setting, you get to choose a fade time for each event separately.

In settings, under "Fading (Gapless Playback)", first pick the event:

* Seek: When you jump around inside a track with the slider.

* Pause/Play: When you press pause or play.

* Manual Track Change: When you press next or previous, or tap a track in the list.

* Automatic Track Change: When one track ends and the next begins on its own. This is the classic crossfade, and it's the one to set for a party playlist.

Then set two sliders for that event:

* Fade In: How long the incoming audio takes to reach full volume.

* Fade Out: How long the outgoing audio takes to go quiet.

Both go from 0 up to 10 seconds. Zero means disabled, so that event switches instantly with no fade at all. Everything starts at zero, so FukeX behaves like a plain player until you decide otherwise.

A good starting point: around 300 ms for Pause/Play and Seek, so it feels soft rather than jarring, and 3 to 5 seconds on Automatic Track Change for proper crossfading between songs.

Changing the event in the dropdown swaps the sliders to that event's own values, so each of the four keeps its own settings.

## The settings screen

In the settings screen, you'll see the following options, grouped under headings:

### Security

* Biometric Unlock: This switch is used to toggle whether your locked playlists can be opened with your fingerprint or face instead of typing the PIN. Turning it on asks you to authenticate once, to prove it's really you. If your device has no biometrics set up, FukeX tells you so instead of silently doing nothing.

* Lock Timeout: Configures how long a temporarily unlocked playlist stays open. Options are Immediate, 1 Minute, 2 Minutes, 5 Minutes, and When Screen Locks.

* Exit Prompt: Asks you for confirmation before closing the app. Off by default. Handy if you keep hitting back one time too many.

### Playback

* Background Playback: Enable this to keep the media playing when you minimize the app. This one is on by default, because a media player that stops when you check a message is not much of a media player.

* Skip Silence: Automatically speeds through quiet or silent parts in the audio. Brilliant for lectures and podcasts, not recommended for music, since it will chew through the quiet passages you actually wanted.

* Skip Unavailable Tracks: Automatically skips any media that fails to load or cannot be played, and shows a short "Skipping unavailable track" message. Useful for SMB playlists where the server might be asleep. With it off, FukeX stops and tells you exactly what went wrong instead, which is what you want when you're trying to work out why something won't play.

* Amplifier Boost: Adjust the volume boost from 0 up to 25 dB, though a warning pops up above 15 dB.

* Enable ReplayGain: Automatically levels out the volume between quiet and loud tracks, using the ReplayGain tag your files already carry if your music library tool wrote one. Off by default.

### Lyrics

* Read Lyrics Aloud and Find Lyrics Online, described in the lyrics section above.

### Visual Icons

* Download, switch on or off, update or delete the optional icon pack, described in the visual icons section above.

### Voice Assistant

* Enable Voice Assistant: Lets you press and hold Volume Down to give a voice command. A short press still lowers the volume.

* Work in Background: Lets you start the assistant while FukeX is minimized. Holding Volume Down in the background needs the "Volume Key Shortcut" (an accessibility service) to be turned on; tap that item to open the accessibility settings. If shake is on, shaking also works in the background while something is playing. The microphone stays off until you trigger the assistant.

* Volume Key Shortcut: Shows whether the FukeX accessibility shortcut is on, and opens your phone's accessibility settings so you can turn it on or off.

* Shake to Wake Assistant: Off by default. Also lets you shake your phone twice to start the assistant.

* Shake Sensitivity: How hard you need to shake, from Very low (a firm shake) to Very high (a light shake). Lower it if the assistant starts by accident when you walk or run.

* Assistant Voice: Choose which installed Text-to-Speech (TTS) engine on your device speaks the voice assistant responses.

* Customize Spoken Feedback: Tick boxes to control what events the assistant speaks for (listening, searching, playing, switching results, controls, or errors and unrecognised speech). When an event is turned off, the assistant plays a short feedback beep instead of speaking.

### Fading (Gapless Playback)

* Event selector, plus the Fade In and Fade Out sliders described in the fading section above.

### About

* About FukeX: Version number, copyright and license information.

* Check for updates: Looks for a newer FukeX on GitHub and installs it if you agree.

* Documentation and License: Open this page and the license from inside the app.

## Where your data is kept

Everything FukeX knows about you sits on your phone, in the app's own private storage:

* Your playlists, track order, and the position you stopped at, live in a file called playlists.json.

* Your playlist PINs live in that same file, but only as a scrambled fingerprint, never as the PIN you typed.

* Your SMB logins live in a separate private file of their own, one entry per server.

* Your settings, and your equalizer settings for each output, live in FukeX's private preferences.

* Lyrics that were found are saved in the app's cache folder.

* Streamed SMB audio is cached in the app's cache folder, up to 500 MB. Local files are never cached, since they are already on your phone. Clearing the app's cache in Android settings empties it.

FukeX never sends any of this anywhere. The only things that leave your phone are the ones you asked for: SMB traffic to your own server, a lyrics lookup to lrclib.net if "Find Lyrics Online" is on, downloading the optional icon pack from GitHub, YouTube searches and streams, and whatever your phone's speech service does with your voice when you use the assistant. Your playlists and your SMB logins are also kept out of Android's automatic cloud backup, so they don't quietly end up in your Google account. Uninstalling FukeX removes the lot. If you want a backup of your playlists, use the export feature.

## Tips and troubleshooting

* A track won't play. Turn "Skip Unavailable Tracks" off for a moment and try again, so FukeX shows you the actual error instead of skipping past it. The usual culprits are a file that has been moved or deleted, or an SMB server that isn't reachable.

* The file picker shows nothing. FukeX likely doesn't have the all files access permission. Go to Android settings, find FukeX, and grant it.

* No playback notification. Notification permission was denied. Grant it in Android settings under FukeX, notifications.

* Playback stops when I leave the app. Check that "Background Playback" is on in settings.

* Music sounds choppy at high volume. Lower the amplifier. Anything past 20 dB distorts on a lot of phones.

* My locked playlist keeps hiding itself. That's the lock timeout doing its job. Set it to a longer value, or unlock the playlist permanently.

* Playback pauses on its own. Check whether something else grabbed the audio, like a call or another player. This is deliberate, and FukeX resumes by itself after a call.

* The voice assistant does not start. Check that "Enable Voice Assistant" is on and that the microphone permission was granted. Hold Volume Down for at least half a second. Outside the app, "Work in Background" and the "Volume Key Shortcut" must both be on.

* The voice assistant does not respond to a shake. Check that "Shake to Wake Assistant" is on, give the phone two firm shakes within a couple of seconds, or raise "Shake Sensitivity". Outside the app, "Work in Background" must be on and something must be playing.

* The voice assistant plays the wrong song. Say the full title, or add the artist's name. It plays the first match, looking through playlists first and then the music library.

* The music library is empty. FukeX needs the music and audio permission, and the songs must be marked as music by Android. Ringtones and recordings are left out.

* Updating from inside FukeX says the update is signed with a different key. You installed FukeX from a build made with another key, such as a test build. Android never updates an app across keys. Export your playlists, uninstall FukeX, and install the release APK.

* The icons did not change after downloading the pack. Check that "Use Visual Icons" is on in settings. If the download failed, try again on a stronger connection.

* The equalizer sounds distorted. Lower the preamp, or lower the bands you boosted.

* An imported SMB playlist won't play. Connect to that server once through "Add media to playlist", so FukeX learns the login for it.

## Accessibility quick reference

Every action below is reachable by putting TalkBack focus on the item and swiping up or down with one finger.

On a playlist tab: Create new playlist, Move Left, Move Right, Hide or Unlock, Export, Remove.

On a track in the list: Play next, Add to queue, Move Up, Move Down, Remove.

On the More options button: Search within playlist, Playlist info, Up next, Lyrics, Equalizer, Enable or Disable Amplifier, Playback Effects, Hide or Unlock, Remove.

On a song in the music library: Play next, Add to queue, Add to playlist.

On a queued track in Up Next: Remove from queue.

On a folder in the file picker or SMB picker: Add entire folder.

On the seek slider and every settings slider: swipe up and down to change the value. The slider reads out its current value as you go, in minutes and seconds for the seek bar, in decibels for the amplifier, in milliseconds for the fade settings, and as a multiplier or a plain level for the playback effects sliders.
