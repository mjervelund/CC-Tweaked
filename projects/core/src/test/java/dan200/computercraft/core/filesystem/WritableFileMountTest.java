// SPDX-FileCopyrightText: 2022 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.filesystem;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import dan200.computercraft.api.filesystem.MountConstants;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.LuaValues;
import dan200.computercraft.test.core.filesystem.WritableMountContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WritableFileMountTest implements WritableMountContract {
    private final List<Path> cleanup = new ArrayList<>();

    @Override
    public MountAccess createMount(long capacity) throws IOException {
        var path = Files.createTempDirectory("cctweaked-test");
        cleanup.add(path);
        return new MountAccessImpl(path.resolve("mount"), capacity);
    }

    @AfterEach
    public void cleanup() throws IOException {
        for (var mount : cleanup) MoreFiles.deleteRecursively(mount, RecursiveDeleteOption.ALLOW_INSECURE);
    }

    @Override
    @Test
    @DisabledOnOs(OS.WINDOWS) // This fails on Windows, and I don't have a debugger to find out why.
    @SuppressWarnings("JUnitMethodInvoked")
    public void Writing_uses_latest_file_size() throws IOException {
        WritableMountContract.super.Writing_uses_latest_file_size();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // Windows doesn't allow deleting a file with open handles.
    public void Deleting_an_open_file_frees_space_on_close() throws IOException {
        var access = createExisting(CAPACITY);
        var mount = access.mount();

        var handle = mount.openFile("file.txt", MountConstants.WRITE_OPTIONS);
        handle.write(LuaValues.encode(LONG_CONTENTS));
        var reader = mount.openFile("file.txt", MountConstants.READ_OPTIONS);

        // The file's data stays on disk while a handle is open, so it must still count against the capacity.
        mount.delete("file.txt");
        assertEquals(CAPACITY - LONG_CONTENTS.length(), mount.getRemainingSpace());

        handle.write(LuaValues.encode("test"));
        assertEquals(CAPACITY - LONG_CONTENTS.length() - 4, mount.getRemainingSpace());

        handle.close();
        assertEquals(CAPACITY - LONG_CONTENTS.length() - 4, mount.getRemainingSpace());

        reader.close();
        assertEquals(CAPACITY, mount.getRemainingSpace());
        assertEquals(access.computeRemainingSpace(), mount.getRemainingSpace(), "Free space is inconsistent");
    }

    private static final class MountAccessImpl implements MountAccess {
        private final Path root;
        private final long capacity;
        private final WritableMount mount;

        private MountAccessImpl(Path root, long capacity) {
            this.root = root;
            this.capacity = capacity;
            mount = new WritableFileMount(root.toFile(), capacity);
        }

        @Override
        public WritableMount mount() {
            return mount;
        }

        @Override
        public void makeReadOnly(String path) {
            Assumptions.assumeTrue(root.resolve(path).toFile().setReadOnly(), "Change file to read-only");
        }

        @Override
        public void ensuresExist() throws IOException {
            Files.createDirectories(root);
        }

        @Override
        public long computeRemainingSpace() {
            return new WritableFileMount(root.toFile(), capacity).getRemainingSpace();
        }
    }
}
