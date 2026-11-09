// SPDX-FileCopyrightText: 2019 The CC: Tweaked Developers
//
// SPDX-License-Identifier: MPL-2.0

package dan200.computercraft.core.computer;

import com.google.common.io.CharStreams;
import dan200.computercraft.api.lua.ILuaAPI;
import dan200.computercraft.api.lua.LuaFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import dan200.computercraft.core.ComputerContext;
import dan200.computercraft.core.computer.mainthread.MainThread;
import dan200.computercraft.core.computer.mainthread.MainThreadConfig;
import dan200.computercraft.core.filesystem.MemoryMount;
import dan200.computercraft.core.terminal.Terminal;
import dan200.computercraft.test.core.computer.BasicEnvironment;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class ComputerTest {
    @Test
    public void testTimeout() {
        assertTimeoutPreemptively(ofSeconds(20), () -> {
            try {
                ComputerBootstrap.run("print('Hello') while true do end", ComputerBootstrap.MAX_TIME);
            } catch (AssertionError e) {
                if (e.getMessage().equals("/test.lua:1: Too long without yielding")) return;
                throw e;
            }

            Assertions.fail("Expected computer to timeout");
        });
    }

    @Test
    public void testDuplicateObjects() {
        class CustomApi implements ILuaAPI {
            @Override
            public String[] getNames() {
                return new String[]{ "custom" };
            }

            @LuaFunction
            public final Object[] getObjects() {
                return new Object[]{ List.of(), List.of() };
            }
        }

        ComputerBootstrap.run("""
            local x, y = custom.getObjects()
            assert(x ~= y)
            """, i -> i.addApi(new CustomApi()), 50);
    }

    /**
     * A start requested while the previous start is still inside its start delay must not survive a shutdown.
     *
     * @see <a href="https://github.com/cc-tweaked/CC-Tweaked/issues/2524">#2524</a>
     */
    @Test
    public void testShutdownCancelsPendingStart() throws InterruptedException {
        var mount = new MemoryMount().addFile("startup.lua", "while true do os.pullEvent() end");
        var environment = new BasicEnvironment(mount);
        var mainThread = new MainThread(new MainThreadConfig.Basic(Integer.MAX_VALUE, Integer.MAX_VALUE));
        var context = ComputerContext.builder(environment).mainThreadScheduler(mainThread).build();
        var computer = new Computer(context, environment, new Terminal(51, 19, true), 0);
        try {
            computer.turnOn();
            for (var tick = 0; tick < 100 && !computer.isOn(); tick++) {
                computer.tick();
                Thread.sleep(50);
            }
            Assertions.assertTrue(computer.isOn(), "Computer should have started");

            // Opening the UI again calls turnOn while the first start is still within the start delay.
            computer.turnOn();
            computer.shutdown();

            for (var tick = 0; tick < 100; tick++) {
                computer.tick();
                Thread.sleep(10);
            }
            Assertions.assertFalse(computer.isOn(), "Computer restarted after being shut down");
        } finally {
            computer.unload();
            for (var tick = 0; tick < 20 && computer.isOn(); tick++) {
                computer.tick();
                Thread.sleep(10);
            }
            context.ensureClosed(1, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) throws Exception {
        var stream = ComputerTest.class.getClassLoader().getResourceAsStream("benchmark.lua");
        try (var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            var contents = CharStreams.toString(reader);
            ComputerBootstrap.run(contents, 1000);
        }
    }
}
