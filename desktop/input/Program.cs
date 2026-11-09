// CONCORD · Jogar junto: recebe comandos do app (stdin, uma linha por comando) e
// injeta teclado/mouse (SendInput) e controles de Xbox virtuais (ViGEmBus).
using System;
using System.Collections.Generic;
using System.Globalization;
using System.Runtime.InteropServices;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

static class Program
{
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Explicit)] struct UNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public UNION u; }
    [StructLayout(LayoutKind.Sequential)] struct RECT { public int l, t, r, b; }
    [StructLayout(LayoutKind.Sequential)] struct POINT { public int x, y; }

    [DllImport("user32.dll", SetLastError = true)] static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] static extern bool IsWindow(IntPtr h);
    [DllImport("user32.dll")] static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] static extern bool SetProcessDpiAwarenessContext(IntPtr v);

    const uint MOVE = 0x1, LDOWN = 0x2, LUP = 0x4, RDOWN = 0x8, RUP = 0x10, MDOWN = 0x20, MUP = 0x40, XDOWN = 0x80, XUP = 0x100, WHEEL = 0x800, HWHEEL = 0x1000;
    const uint KEY_EXT = 0x1, KEY_UP = 0x2, KEY_SCAN = 0x8;

    static readonly HashSet<int> heldKeys = new HashSet<int>();
    static readonly HashSet<int> heldBtns = new HashSet<int>();
    static ViGEmClient vigem;
    static readonly Dictionary<int, IXbox360Controller> pads = new Dictionary<int, IXbox360Controller>();
    static readonly Xbox360Button[] BTN = {
        Xbox360Button.A, Xbox360Button.B, Xbox360Button.X, Xbox360Button.Y, Xbox360Button.LeftShoulder, Xbox360Button.RightShoulder,
        null, null, Xbox360Button.Back, Xbox360Button.Start, Xbox360Button.LeftThumb, Xbox360Button.RightThumb,
        Xbox360Button.Up, Xbox360Button.Down, Xbox360Button.Left, Xbox360Button.Right, Xbox360Button.Guide };

    static void Send(INPUT i) { SendInput(1, new[] { i }, Marshal.SizeOf(typeof(INPUT))); }
    static void Mouse(uint flags, int dx = 0, int dy = 0, uint data = 0) { var i = new INPUT { type = 0 }; i.u.mi = new MOUSEINPUT { dx = dx, dy = dy, dwFlags = flags, mouseData = data }; Send(i); }
    static void Key(int sc, bool ext, bool down)
    {
        var i = new INPUT { type = 1 }; i.u.ki = new KEYBDINPUT { wScan = (ushort)sc, dwFlags = KEY_SCAN | (ext ? KEY_EXT : 0) | (down ? 0 : KEY_UP) }; Send(i);
        int id = sc | (ext ? 0x10000 : 0); if (down) heldKeys.Add(id); else heldKeys.Remove(id);
    }
    static void Button(int b, bool down)
    {
        uint f; uint data = 0;
        switch (b) { case 0: f = down ? LDOWN : LUP; break; case 1: f = down ? MDOWN : MUP; break; case 2: f = down ? RDOWN : RUP; break;
            case 3: f = down ? XDOWN : XUP; data = 1; break; case 4: f = down ? XDOWN : XUP; data = 2; break; default: return; }
        Mouse(f, 0, 0, data); if (down) heldBtns.Add(b); else heldBtns.Remove(b);
    }
    static void Reset()
    {
        foreach (var k in new List<int>(heldKeys)) Key(k & 0xFFFF, (k & 0x10000) != 0, false);
        foreach (var b in new List<int>(heldBtns)) Button(b, false);
        foreach (var p in pads.Values) { try { Zero(p); } catch { } }
    }
    static void Zero(IXbox360Controller c)
    {
        foreach (var b in BTN) if (b != null) c.SetButtonState(b, false);
        c.SetAxisValue(Xbox360Axis.LeftThumbX, 0); c.SetAxisValue(Xbox360Axis.LeftThumbY, 0); c.SetAxisValue(Xbox360Axis.RightThumbX, 0); c.SetAxisValue(Xbox360Axis.RightThumbY, 0);
        c.SetSliderValue(Xbox360Slider.LeftTrigger, 0); c.SetSliderValue(Xbox360Slider.RightTrigger, 0); c.SubmitReport();
    }
    static IXbox360Controller Pad(int slot)
    {
        if (vigem == null) return null;
        if (pads.TryGetValue(slot, out var c)) return c;
        c = vigem.CreateXbox360Controller(); c.AutoSubmitReport = false; c.Connect(); pads[slot] = c; return c;
    }
    static int I(string s) => int.Parse(s, CultureInfo.InvariantCulture);

    static void Main()
    {
        try { SetProcessDpiAwarenessContext(new IntPtr(-4)); } catch { }
        string err = "";
        try { vigem = new ViGEmClient(); } catch (Exception e) { vigem = null; err = e.GetType().Name; }
        Console.Out.WriteLine("ready vigem=" + (vigem != null ? "1" : "0") + (err != "" ? " err=" + err : "")); Console.Out.Flush();
        string line;
        while ((line = Console.In.ReadLine()) != null)
        {
            var a = line.Split(' ');
            try
            {
                switch (a[0])
                {
                    case "pos": SetCursorPos(I(a[1]), I(a[2])); break;
                    case "win": // hwnd nx ny (0..10000) relativo à área do jogo
                        {
                            var h = new IntPtr(long.Parse(a[1], CultureInfo.InvariantCulture));
                            if (!IsWindow(h) || !GetClientRect(h, out var r)) break;
                            var p = new POINT { x = (int)((r.r - r.l) * I(a[2]) / 10000.0), y = (int)((r.b - r.t) * I(a[3]) / 10000.0) };
                            ClientToScreen(h, ref p); SetCursorPos(p.x, p.y); break;
                        }
                    case "fg": { var h = new IntPtr(long.Parse(a[1], CultureInfo.InvariantCulture)); if (IsWindow(h)) SetForegroundWindow(h); break; }
                    case "rel": Mouse(MOVE, I(a[1]), I(a[2])); break;
                    case "btn": Button(I(a[1]), a[2] == "1"); break;
                    case "whl": Mouse(WHEEL, 0, 0, unchecked((uint)I(a[1]))); break;
                    case "hwl": Mouse(HWHEEL, 0, 0, unchecked((uint)I(a[1]))); break;
                    case "key": Key(I(a[1]), a[2] == "1", a[3] == "1"); break;
                    case "pad":
                        {
                            var c = Pad(I(a[1])); if (c == null) break;
                            int bits = I(a[2]);
                            for (int i = 0; i < BTN.Length; i++) if (BTN[i] != null) c.SetButtonState(BTN[i], (bits & (1 << i)) != 0);
                            c.SetAxisValue(Xbox360Axis.LeftThumbX, (short)I(a[3])); c.SetAxisValue(Xbox360Axis.LeftThumbY, (short)I(a[4]));
                            c.SetAxisValue(Xbox360Axis.RightThumbX, (short)I(a[5])); c.SetAxisValue(Xbox360Axis.RightThumbY, (short)I(a[6]));
                            c.SetSliderValue(Xbox360Slider.LeftTrigger, (byte)I(a[7])); c.SetSliderValue(Xbox360Slider.RightTrigger, (byte)I(a[8]));
                            c.SubmitReport(); break;
                        }
                    case "padoff": { int s = I(a[1]); if (pads.TryGetValue(s, out var c)) { try { c.Disconnect(); } catch { } pads.Remove(s); } break; }
                    case "reset": Reset(); break;
                    case "ping": Console.Out.WriteLine("pong"); Console.Out.Flush(); break;
                }
            }
            catch (Exception e) { Console.Error.WriteLine(e.Message); }
        }
        Reset();
        foreach (var p in pads.Values) { try { p.Disconnect(); } catch { } }
    }
}
