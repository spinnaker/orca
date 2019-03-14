package de.huxhorn.sulky.ulid

import java.util.Random

@Deprecated("Upstream ULID dependency now has full support for required Spinnaker functionality")
class SpinULID(random: Random): ULID(random)
