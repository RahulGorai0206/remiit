# Fix App-Launch Trigger Noise

The app-launch trigger currently fires multiple times for the same app (e.g., YouTube) when window states change (like entering full-screen) if they happen more than 2 seconds apart. It also fails to reliably detect re-entry from the home screen because the launcher is ignored before state is updated.

## Proposed Changes

### App Launch Logic

#### [MODIFY] [AppLaunchDispatcher.kt](file:///Users/rahul/AndroidStudioProjects/remiit/app/src/main/java/com/rahulgorai/remiit/trigger/applaunch/AppLaunchDispatcher.kt)

- Separate "transient" system packages (like SystemUI) from "excluded" apps (like the Launcher).
- **Transient packages**: Completely ignored. Pulling down the notification shade will not count as leaving the current app.
- **Excluded packages**: Update the "last seen" state but do not fire triggers. This ensures that going to the Home screen and back to an app correctly counts as a new launch.
- **Deduplication**: Remove the 2-second timeout for the same package. If the package hasn't changed, it's a window/rotation change and should never fire twice.

## Verification Plan

### Manual Verification
1. Open YouTube. Trigger should fire once.
2. Toggle full-screen mode in YouTube. Trigger should **not** fire again.
3. Rotate the device while in YouTube. Trigger should **not** fire again.
4. Go to the Home screen.
5. Open YouTube again. Trigger **should** fire.
6. Pull down the notification shade while in YouTube and push it back up. Trigger should **not** fire.
