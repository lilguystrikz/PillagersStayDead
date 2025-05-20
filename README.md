# Pillagers Stay Dead

# Main Features:

This mod stops pillagers from spawning in an outpost where all pillagers have been killed. Outposts will be scanned and pillagers that are found within the bounding box of an outpost (within 72 blocks) will be considered as part of the outpost. Once all pillagers within the outpost's bounding box have been killed, the outpost is considered neutralized and they are prevented from spawning.

## Integrations:

This mod supports modded outpost structures from:

Terralith

CTOV (ChoiceTheorem's Overhauled Villages)

Towns and Towers

## Compatibility

This mod has been tested with chunk optimization mods like C2ME. It shouldn't break with other mods installed, but anything is possible.

## Problems

If you teleport directly to an outpost and command kill all pillagers then the pillager may continue to spawn. The scanning algorithm takes anywhere from 1-30 seconds to identify an outpost and track pillager deaths. This means that if you kill the pillagers before the outpost is scanned, the pillagers will respawn. This shouldn't be an issue in survival mode or in most scenarios in general.