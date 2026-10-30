# Synthetic conversion fixture

`smp-silent.flac` is one second of mono 44.1 kHz silence, generated locally for the NCM/MediaStore smoke test. It contains no third-party music.

Reproduce with FFmpeg:

```text
ffmpeg -f lavfi -i anullsrc=r=44100:cl=mono -t 1 -c:a flac -map_metadata -1 smp-silent.flac
```

`NcmSmokeAudit` constructs a bounded NCM input from this fixture at runtime using the documented format, converts it through the production converter, publishes it to `MediaStore.Audio`, checks the system-reported duration, and deletes the temporary media entry. Its record test uses a separate temporary library and synthetic source IDs.
