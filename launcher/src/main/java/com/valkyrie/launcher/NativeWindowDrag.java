package com.valkyrie.launcher;

import com.sun.jna.Pointer;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.WPARAM;

final class NativeWindowDrag {
    private static final int WM_NCLBUTTONDOWN = 0x00A1;
    private static final int HTCAPTION = 2;
    private static final User32Extra USER32 = Native.load("user32", User32Extra.class);

    private NativeWindowDrag() {}

    static boolean begin(String title) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return false;
        HWND window = findWindow(title);
        if (window == null) return false;
        USER32.ReleaseCapture();
        User32.INSTANCE.SendMessage(window, WM_NCLBUTTONDOWN, new WPARAM(HTCAPTION), new LPARAM(0));
        return true;
    }

    private static HWND findWindow(String title) {
        int processId = Math.toIntExact(ProcessHandle.current().pid());
        HWND[] result = new HWND[1];
        User32.INSTANCE.EnumWindows((window, data) -> {
            IntByReference owner = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, owner);
            if (owner.getValue() != processId || !User32.INSTANCE.IsWindowVisible(window)) return true;
            char[] text = new char[256];
            User32.INSTANCE.GetWindowText(window, text, text.length);
            String windowTitle = Pointer.nativeValue(window.getPointer()) == 0 ? "" : com.sun.jna.Native.toString(text);
            if (!title.equals(windowTitle)) return true;
            result[0] = window;
            return false;
        }, null);
        return result[0];
    }

    private interface User32Extra extends Library {
        boolean ReleaseCapture();
    }
}
