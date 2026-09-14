package com.refiq.platform.user.internal.logging;

import org.slf4j.Logger;
import java.util.UUID;

/**
 * Domain-specific structured log events for the User module.
 * Implemented as a sealed interface to guarantee exhaustive pattern matching.
 * Placed in an internal logging package to maintain consistency across domain modules.
 */
public sealed interface UserLogEvent {

  record ProfileCreated(UUID id, String name) implements UserLogEvent {}
  record ProfileRetrieved(UUID id) implements UserLogEvent {}
  record ProfileNotFound(UUID id) implements UserLogEvent {}

  default void log(Logger logger) {
    switch (this) {
      case ProfileCreated e -> logger.atInfo()
          .setMessage("Hospital profile successfully created")
          .addKeyValue("id", e.id())
          .addKeyValue("name", e.name())
          .log();

      case ProfileRetrieved e -> logger.atDebug()
          .setMessage("Profile retrieved")
          .addKeyValue("id", e.id())
          .log();

      case ProfileNotFound e -> logger.atWarn()
          .setMessage("Attempted to retrieve a non-existent profile")
          .addKeyValue("id", e.id())
          .log();
    }
  }
}