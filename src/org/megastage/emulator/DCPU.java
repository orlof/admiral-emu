package org.megastage.emulator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Experimental 1.7 update to Notch's 1.4 emulator
 * @author Notch, Herobrine
 *
 */
public class DCPU
{
    public char[] ram = new char[65536];
    public char pc;
    public char sp;
    public char ex;
    public char ia;
    public char[] registers = new char[8];
    public int cycles;
    protected ArrayList<DCPUHardware> hardware = new ArrayList<DCPUHardware>();

    protected static volatile boolean stop = false;
    protected static final int khz = 100;
    boolean isSkipping = false;
    boolean isOnFire = false;
    boolean queueingEnabled = false; //TODO: Verify implementation
    char[] interrupts = new char[256];
    int ip;
    int iwp;

    public int getAddrB(int type)
    {
        switch (type & 0xF8) {
            case 0x00:
                return 0x10000 + (type & 0x7);
            case 0x08:
                return registers[type & 0x7];
            case 0x10:
                cycles++;
                return ram[pc++] + registers[type & 0x7] & 0xFFFF;
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return (--sp) & 0xFFFF;
                    case 0x1:
                        return sp & 0xFFFF;
                    case 0x2:
                        cycles++;
                        return ram[pc++] + sp & 0xFFFF;
                    case 0x3:
                        return 0x10008;
                    case 0x4:
                        return 0x10009;
                    case 0x5:
                        return 0x10010;
                    case 0x6:
                        cycles++;
                        return ram[pc++];
                }
                cycles++;
                return 0x20000 | ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public int getAddrA(int type) {
        if (type >= 0x20) {
            return 0x20000 | (type & 0x1F) + 0xFFFF & 0xFFFF;
        }

        switch (type & 0xF8) {
            case 0x00:
                return 0x10000 + (type & 0x7);
            case 0x08:
                return registers[type & 0x7];
            case 0x10:
                cycles++;
                return ram[pc++] + registers[type & 0x7] & 0xFFFF;
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return sp++ & 0xFFFF;
                    case 0x1:
                        return sp & 0xFFFF;
                    case 0x2:
                        cycles++;
                        return ram[pc++] + sp & 0xFFFF;
                    case 0x3:
                        return 0x10008;
                    case 0x4:
                        return 0x10009;
                    case 0x5:
                        return 0x10010;
                    case 0x6:
                        cycles++;
                        return ram[pc++];
                }
                cycles++;
                return 0x20000 | ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public char getValA(int type) {
        if (type >= 0x20) {
            return (char)((type & 0x1F) + 0xFFFF);
        }

        switch (type & 0xF8) {
            case 0x00:
                return registers[type & 0x7];
            case 0x08:
                return ram[registers[type & 0x7]];
            case 0x10:
                cycles++;
                return ram[ram[pc++] + registers[type & 0x7] & 0xFFFF];
            case 0x18:
                switch (type & 0x7) {
                    case 0x0:
                        return ram[sp++ & 0xFFFF];
                    case 0x1:
                        return ram[sp & 0xFFFF];
                    case 0x2:
                        cycles++;
                        return ram[ram[pc++] + sp & 0xFFFF];
                    case 0x3:
                        return sp;
                    case 0x4:
                        return pc;
                    case 0x5:
                        return ex;
                    case 0x6:
                        cycles++;
                        return ram[ram[pc++]];
                }
                cycles++;
                return ram[pc++];
        }

        throw new IllegalStateException("Illegal a value type " + Integer.toHexString(type) + "! How did you manage that!?");
    }

    public char get(int addr) {
        if (addr < 0x10000)
            return ram[addr & 0xFFFF];
        if (addr < 0x10008)
            return registers[addr & 0x7];
        if (addr >= 0x20000)
            return (char)addr;
        if (addr == 0x10008)
            return sp;
        if (addr == 0x10009)
            return pc;
        if (addr == 0x10010)
            return ex;
        throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?");
    }

    public void set(int addr, char val) {
        if (addr < 0x10000)
            ram[addr & 0xFFFF] = val;
        else if (addr < 0x10008) {
            registers[addr & 0x7] = val;
        } else if (addr < 0x20000) {
            if (addr == 0x10008)
                sp = val;
            else if (addr == 0x10009)
                pc = val;
            else if (addr == 0x10010)
                ex = val;
            else
                throw new IllegalStateException("Illegal address " + Integer.toHexString(addr) + "! How did you manage that!?");
        }
    }

    public static int getInstructionLength(char opcode) {
        int len = 1;
        int cmd = opcode & 0x1F;
        if (cmd == 0) {
            cmd = opcode >> 5 & 0x1F;
            if (cmd > 0) {
                int atype = opcode >> 10 & 0x3F;
                if (((atype & 0xF8) == 16) || (atype == 31) || (atype == 30)) len++;
            }
        }
        else {
            int atype = opcode >> 5 & 0x1F;
            int btype = opcode >> 10 & 0x3F;
            if (((atype & 0xF8) == 16) || (atype == 31) || (atype == 30)) len++;
            if (((btype & 0xF8) == 16) || (btype == 31) || (btype == 30)) len++;
        }
        return len;
    }

    public void skip() {
        isSkipping = true;
    }

    public void tick() {
        cycles++;

        if (isOnFire) {
//      cycles += 10; //Disabled to match speed of crashing seen in livestreams
            /* For Java 7+
              int pos = ThreadLocalRandom.current().nextInt();
            char val = (char) (pos >> 16);//(char) ThreadLocalRandom.current().nextInt(65536);
            int len = (int)(1 / (ThreadLocalRandom.current().nextFloat() + 0.001f)) - 80;
            */
            int pos = (int)(Math.random() * 0x10000) & 0xFFFF;
            char val = (char) ((int)(Math.random() * 0x10000) & 0xFFFF);
            int len = (int)(1 / (Math.random() + 0.001f)) - 0x50;
            for (int i = 0; i < len; i++) {
                ram[(pos + i) & 0xFFFF] = val;
            }
        }

        if (isSkipping) {
            char opcode = ram[pc];
            int cmd = opcode & 0x1F;
            pc = (char)(pc + getInstructionLength(opcode));
            if ((cmd >= 16) && (cmd <= 23))
                isSkipping = true;
            else {
                isSkipping = false;
            }
            return;
        }

        if (!queueingEnabled) {
            if (ip != iwp) {
                char a = interrupts[ip = ip + 1 & 0xFF];
                if (ia > 0) {
                    queueingEnabled = true;
                    ram[--sp & 0xFFFF] = pc;
                    ram[--sp & 0xFFFF] = registers[0];
                    registers[0] = a;
                    pc = ia;
                }
            }
        }

        char opcode = ram[pc++];

        int cmd = opcode & 0x1F;
        if (cmd == 0) {
            cmd = opcode >> 5 & 0x1F;
            if (cmd != 0)
            {
                int atype = opcode >> 10 & 0x3F;
                int aaddr = getAddrA(atype);
                char a = get(aaddr);

                switch (cmd) {
                    case 1: //JSR
                        cycles += 2;
                        ram[--sp & 0xFFFF] = pc;
                        pc = a;
                        break;
//        case 7: //HCF
//          cycles += 8;
//          isOnFire = true;
//          break;
                    case 8: //INT
                        cycles += 3;
                        interrupt(a);
                        break;
                    case 9: //IAG
                        set(aaddr, ia);
                        break;
                    case 10: //IAS
                        ia = a;
                        break;
                    case 11: //RFI
                        cycles += 2;
                        //disables interrupt queueing, pops A from the stack, then pops PC from the stack
                        queueingEnabled = false;
                        registers[0] = ram[sp++ & 0xFFFF];
                        pc = ram[sp++ & 0xFFFF];
                        break;
                    case 12: //IAQ
                        cycles++;
                        //if a is nonzero, interrupts will be added to the queue instead of triggered. if a is zero, interrupts will be triggered as normal again
                        if (a == 0) {
                            queueingEnabled = false;
                        } else {
                            queueingEnabled = true;
                        }
                        break;
                    case 16: //HWN
                        cycles++;
                        set(aaddr, (char)hardware.size());
                        break;
                    case 17: //HWQ
                        cycles += 3;
                        synchronized (hardware) {
                            if ((a >= 0) && (a < hardware.size())) {
                                ((DCPUHardware)hardware.get(a)).query();
                            }
                        }
                        break;
                    case 18: //HWI
                        cycles += 3;
                        synchronized (hardware) {
                            if ((a >= 0) && (a < hardware.size())) {
                                ((DCPUHardware)hardware.get(a)).interrupt();
                            }
                        }
                        break;
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 13:
                    case 14:
                    case 15:
                    default:
                        break;
                }
            }
        } else {
            int atype = opcode >> 10 & 0x3F;

            char a = getValA(atype);

            int btype = opcode >> 5 & 0x1F;
            int baddr = getAddrB(btype);
            char b = get(baddr);

            switch (cmd) {
                case 1: //SET
                    b = a;
                    break;
                case 2:{ //ADD
                    cycles++;
                    int val = b + a;
                    b = (char)val;
                    ex = (char)(val >> 16);
                    break;
                }case 3:{ //SUB
                    cycles++;
                    int val = b - a;
                    b = (char)val;
                    ex = (char)(val >> 16);
                    break;
                }case 4:{ //MUL
                    cycles++;
                    int val = b * a;
                    b = (char)val;
                    ex = (char)(val >> 16);
                    break;
                }case 5:{ //MLI
                    cycles++;
                    int val = (short)b * (short)a;
                    b = (char)val;
                    ex = (char)(val >> 16);
                    break;
                }case 6:{ //DIV
                    cycles += 2;
                    if (a == 0) {
                        b = ex = 0;
                    } else {
                        b /= a;
                        ex = (char)((b << 16) / a);
                    }
                    break;
                }case 7:{ //DVI
                    cycles += 2;
                    if (a == 0) {
                        b = ex = 0;
                    } else {
                        b = (char)((short)b / (short)a);
                        ex = (char)(((short)b << 16) / (short)a);
                    }
                    break;
                }case 8: //MOD
                    cycles += 2;
                    if (a == 0)
                        b = 0;
                    else {
                        b = (char)(b % a);
                    }
                    break;
                case 9: //MDI
                    cycles += 2;
                    if (a == 0)
                        b = 0;
                    else {
                        b = (char)((short)b % (short)a);
                    }
                    break;
                case 10: //AND
                    b = (char)(b & a);
                    break;
                case 11: //BOR
                    b = (char)(b | a);
                    break;
                case 12: //XOR
                    b = (char)(b ^ a);
                    break;
                case 13: //SHR
                    ex = (char)(b << 16 >> a);
                    b = (char)(b >>> a);
                    break;
                case 14: //ASR
                    ex = (char)((short)b << 16 >>> a);
                    b = (char)((short)b >> a);
                    break;
                case 15: //SHL
                    ex = (char)(b << a >> 16);
                    b = (char)(b << a);
                    break;
                case 16: //IFB
                    cycles++;
                    if ((b & a) == 0) skip();
                    return;
                case 17: //IFC
                    cycles++;
                    if ((b & a) != 0) skip();
                    return;
                case 18: //IFE
                    cycles++;
                    if (b != a) skip();
                    return;
                case 19: //IFN
                    cycles++;
                    if (b == a) skip();
                    return;
                case 20: //IFG
                    cycles++;
                    if (b <= a) skip();
                    return;
                case 21: //IFA
                    cycles++;
                    if ((short)b <= (short)a) skip();
                    return;
                case 22: //IFL
                    cycles++;
                    if (b >= a) skip();
                    return;
                case 23: //IFU
                    cycles++;
                    if ((short)b >= (short)a) skip();
                    return;
                case 26:{ //ADX
                    cycles++;
                    int val = b + a + ex;
                    b = (char)val;
                    ex = (char)(val >> 16);
                    break;
                }case 27:{ //SBX
                    cycles++;
                    int val = b - a + ex;
                    b = (char)val;
                    ex = (char)(val >> 16);
                    break;
                }case 30: //STI
                    b = a;
                    set(baddr, b);
                    registers[6]++;
                    registers[7]++;
                    return;
                case 31: //STD
                    b = a;
                    set(baddr, b);
                    registers[6]--;
                    registers[7]--;
                    return;
                case 24:
                case 25:
            }
            set(baddr, b);
        }
    }

    public void interrupt(char a)
    {
        interrupts[iwp = iwp + 1 & 0xFF] = a;
        if (iwp == ip) isOnFire = true;
    }

    public void tickHardware() {
        synchronized (hardware) {
            for (int i = 0; i < hardware.size(); i++) {
                ((DCPUHardware)hardware.get(i)).tick60hz();
            }
        }
    }

    public boolean addHardware(DCPUHardware hw) {
        synchronized (hardware) {
            return hardware.add(hw);
        }
    }

    public boolean removeHardware(DCPUHardware hw) {
        synchronized (hardware) {
            return hardware.remove(hw);
        }
    }

    public List<DCPUHardware> getHardware() {
        //TODO sync elsewhere
        synchronized (hardware) {
            return new ArrayList<DCPUHardware>(hardware);
        }
    }

    public void run() {
        (new Thread() {
            @Override
            public void run() {
                for (DCPUHardware hw : hardware) {
                    hw.powerOn();
                }
                int hz = 1000 * khz;
                int cyclesPerFrame = hz / 60 + 1;

                long nsPerFrame = 16666666L;
                long nextTime = System.nanoTime();

                while (true) {
                    while (System.nanoTime() < nextTime) {
                        try {
                            Thread.sleep(1L);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    while (cycles < cyclesPerFrame) {
                        tick();
                    }
                    tickHardware();
                    cycles -= cyclesPerFrame;
                    nextTime += nsPerFrame;
                }
            }
        }).start();
    }

    public void load(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(is));
        int i = 0;
        try {
            for (; i<ram.length; i++) {
                ram[i] = dis.readChar();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        } catch (IOException e) {
            for (; i<ram.length; i++) {
                ram[i] = 0;
            }
        } finally {
            dis.close();
        }
    }

    public static void main(String[] args) throws Exception {
        final DCPU dcpu = new DCPU();

        if(args.length > 0) {
            InputStream is = new FileInputStream(new File(args[0]));
            dcpu.load(is);
        } else {
            InputStream is = DCPU.class.getResourceAsStream("admiral.bin");
            dcpu.load(is);
        }

        final VirtualClock clock = new VirtualClock();
        clock.connectTo(dcpu);

        final VirtualKeyboard kbd = new VirtualKeyboard(new AWTKeyMapping(true));
        kbd.connectTo(dcpu);

        final VirtualFloppyDrive floppy = new VirtualFloppyDrive();
        floppy.connectTo(dcpu);

        if(args.length > 1) {
            InputStream is = new FileInputStream(new File(args[1]));
            floppy.insert(new FloppyDisk(is));
        } else {
            InputStream is = DCPU.class.getResourceAsStream("floppy.bin");
            floppy.insert(new FloppyDisk(is));
        }


        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    kbd.keyPressed(e.getKeyCode());
                } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                    kbd.keyReleased(e.getKeyCode());
                } else if (e.getID() == KeyEvent.KEY_TYPED) {
                    kbd.keyTyped(e.getKeyChar());
                }
                return false;
            }
        });

        final VirtualMonitor mon = new VirtualMonitor();
        mon.connectTo(dcpu);

        LEM1802Viewer view = new LEM1802Viewer();
        view.attach(mon);

        JFrame f = new JFrame();
        f.setSize(640, 400);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.add(view.canvas, BorderLayout.CENTER);

        f.getContentPane().add(p);
        f.setVisible(true);
        f.createBufferStrategy(2);

        dcpu.run();
        view.canvas.setup();
    }

}
