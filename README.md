# Interactive Login

Paper plugin that extends AuthMe (or AuthMeReloaded) with interactive third-party logins.

Currently supports Discord as an external source, allowing registration only for users with a specific role.

## Dev Setup

 1. Download [PaperMC](https://papermc.io/) and set up a local server;
 2. Build the project with `./gradlew build shadowJar`;
 3. Copy (or symlink) the `build/libs/InteractiveLogin-vX.X-all.jar` to the `plugins/` folder of the server;
 4. Run the server once so it populates the default config;
 5. Edit the config with your [Discord Bot](https://discord.com/developers/) credentials and server/role IDs;
 6. Run the server again, now it should work dandily.
