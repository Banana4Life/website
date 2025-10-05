package service

import io.circe.derivation

given derivation.Configuration = derivation.Configuration.default.withDefaults