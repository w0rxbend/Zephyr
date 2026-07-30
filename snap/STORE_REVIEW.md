# Snap Store publication checklist

Zephyr's snap needs `confinement: classic`. The application manages the
user's existing SDKMAN installation, executes SDKMAN's shell functions and
installed toolchains, and can launch an activated host terminal. Strict
confinement cannot provide those host-development-environment semantics.

Before the release workflow can publish:

1. Sign in to Snapcraft and register the `zephyr-sdkman` name.
2. Submit a classic-confinement request in Canonical's store-requests forum.
   Zephyr fits the documented supported category “tools for local, non-root
   user driven configuration of/switching to development
   workspaces/environments.”
3. After approval, create a least-privilege store credential:

   ```shell
   snapcraft export-login \
     --snaps=zephyr-sdkman \
     --channels=stable \
     --acls=package_access,package_push,package_update,package_release \
     --expires=2027-07-30 \
     zephyr-snapcraft-login.txt
   ```

4. Save that file's complete contents as the repository Actions secret
   `SNAPCRAFT_STORE_CREDENTIALS`.
5. Delete the exported credential file after the secret is configured.

Without the secret or while review is pending, both architecture-specific
`.snap` files are still built and attached to the GitHub release. A failed
Snap Store upload is reported but cannot discard successful release packages.
