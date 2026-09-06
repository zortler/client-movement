
# Client-Movement

Fabric mod for Minecraft 26.2 making sprinting and poses entirely client-sided with no server interference. This greatly reduces the amount of movement glitches and inconsistencies for players with high ping and gets rid of the ancient FOV-wobble glitch (MC-4098).
___

This mod could possibly get flagged by certain AntiCheats and might be seen by some as an unfair advantage (you don't lose sprint from hitting non-player entities anymore, but that is purely visual when holding sprint and doesn't apply for PvP), so use at your own discretion.


## Development

Requires Java 25. Build with `./gradlew build`; run the client packet regression test with `./gradlew runClientGameTest` (use `xvfb-run -a` on headless Linux). The test checks local sprint and pose preservation, other attribute updates, and remote entities in a real 26.2 client.
