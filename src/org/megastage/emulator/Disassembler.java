package org.megastage.emulator;

import java.util.ArrayList;

public class Disassembler {
    private final char[] binary;
    private int addr, line;
    private StringBuilder sb = new StringBuilder();
    private ArrayList<Integer> dat = new ArrayList<>(4);
    private int startaddr;

    public Disassembler(char[] binary) {
        this.binary = binary;
        this.addr = 0;
    }

    public String disassemble() {
        try {
            while (true) {
                startaddr = addr;
                char c = binary[addr++];
                int op = c & 0b11111;

                switch (op) {
                    case 0x01:
                        basic("SET", c);
                        break;
                    case 0x02:
                        basic("ADD", c);
                        break;
                    case 0x03:
                        basic("SUB", c);
                        break;
                    case 0x04:
                        basic("MUL", c);
                        break;
                    case 0x05:
                        basic("MLI", c);
                        break;
                    case 0x06:
                        basic("DIV", c);
                        break;
                    case 0x07:
                        basic("DVI", c);
                        break;
                    case 0x08:
                        basic("MOD", c);
                        break;
                    case 0x09:
                        basic("MDI", c);
                        break;
                    case 0x0a:
                        basic("AND", c);
                        break;
                    case 0x0b:
                        basic("BOR", c);
                        break;
                    case 0x0c:
                        basic("XOR", c);
                        break;
                    case 0x0d:
                        basic("SHR", c);
                        break;
                    case 0x0e:
                        basic("ASR", c);
                        break;
                    case 0x0f:
                        basic("SHL", c);
                        break;
                    case 0x10:
                        basic("IFB", c);
                        break;
                    case 0x11:
                        basic("IFC", c);
                        break;
                    case 0x12:
                        basic("IFE", c);
                        break;
                    case 0x13:
                        basic("IFN", c);
                        break;
                    case 0x14:
                        basic("IFG", c);
                        break;
                    case 0x15:
                        basic("IFA", c);
                        break;
                    case 0x16:
                        basic("IFL", c);
                        break;
                    case 0x17:
                        basic("IFU", c);
                        break;
                    case 0x1a:
                        basic("ADX", c);
                        break;
                    case 0x1b:
                        basic("SBX", c);
                        break;
                    case 0x1e:
                        basic("STI", c);
                        break;
                    case 0x1f:
                        basic("STD", c);
                        break;

                    case 0x00:
                        special(c);
                        break;

                    default:
                        addData(c & 0xffff);
                }
            }
        } catch(ArrayIndexOutOfBoundsException ex) {
        }
        closeDat();
        return sb.toString();
    }

    private void addData(int d) {
        if(dat.size() == 4) {
            closeDat();
        }

        dat.add(d);
    }

    private void closeDat() {
        if(dat.size() > 0) {
            sb.append("\n");
            sb.append("" + (line++) + "\n");
            sb.append("" + (dat.size()) + "\n");
            for(int i=addr-dat.size(); i < addr; i++) {
                sb.append(String.format("%04X\n", i));
                sb.append(String.format("%04X\n", (int) binary[i]));
            }
            sb.append("DAT ");
            for (int i = 0; i < dat.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(String.format("%04X", dat.get(i)));
            }
            sb.append("\n");
            dat.clear();
        }
    }

    private void special(char c) {
        closeDat();
        int a = (c >> 10) & 0b111111;
        int op = (c >> 5) & 0b11111;
        String aa = addrA(a, true);

        sb.append("\n");
        sb.append("" + (line++) + "\n");
        sb.append("" + (addr - startaddr) + "\n");
        for(int i=startaddr; i < addr; i++) {
            sb.append(String.format("%04X\n", i));
            sb.append(String.format("%04X\n", (int) binary[i]));
        }

        switch(op) {
            case 0x01:
                sb.append("JSR ").append(aa).append("\n");
                break;
            case 0x08:
                sb.append("INT ").append(aa).append("\n");
                break;
            case 0x09:
                sb.append("IAG ").append(aa).append("\n");
                break;
            case 0x0a:
                sb.append("IAS ").append(aa).append("\n");
                break;
            case 0x0b:
                sb.append("RFI ").append(aa).append("\n");
                break;
            case 0x0c:
                sb.append("IAQ ").append(aa).append("\n");
                break;
            case 0x10:
                sb.append("HWN ").append(aa).append("\n");
                break;
            case 0x11:
                sb.append("HWQ ").append(aa).append("\n");
                break;
            case 0x12:
                sb.append("HWI ").append(aa).append("\n");
                break;
        }

    }

    private void basic(String cmd, char c) {
        closeDat();
        int a = (c >> 10) & 0b111111;
        int b = (c >> 5) & 0b11111;
        String aa = addrA(a, true);
        String bb = addrA(b, false);

        sb.append("\n");
        sb.append("" + (line++) + "\n");
        sb.append("" + (addr - startaddr) + "\n");
        for(int i=startaddr; i < addr; i++) {
            sb.append(String.format("%04X\n", i));
            sb.append(String.format("%04X\n", (int) binary[i]));
        }

        sb.append(cmd).append(" ").append(bb).append(", ").append(aa).append("\n");
    }

    private static final String[] REG = new String[] {
            "A", "B", "C", "X", "Y", "Z", "I", "J",
            "[A]", "[B]", "[C]", "[X]", "[Y]", "[Z]", "[I]", "[J]"
    };

    private String addrA(int a, boolean isA) {
        if(a < 0x10) {
            return REG[a];
        }
        if(a < 0x18) {
            return "[" + REG[a-0x10] + " + " + hex(addr++) + "]";
        }
        if(a == 0x18) {
            return isA ? "POP": "PUSH";
        }
        if(a == 0x19) {
            return "[SP]";
        }
        if(a < 0x1a) {
            return "[SP + " + hex(addr++) + "]";
        }
        if(a == 0x1b) {
            return "SP";
        }
        if(a == 0x1c) {
            return "PC";
        }
        if(a == 0x1d) {
            return "EX";
        }
        if(a < 0x1e) {
            return "[" + hex(addr++) + "]";
        }
        if(a < 0x1f) {
            return hex(addr++);
        }
        if(a < 0x40) {
            return hex(a - 0x21);
        }
        throw new RuntimeException();
    }

    private String hex(int i) {
        return String.format("0x%04X", i & 0xffff);
    }
}
