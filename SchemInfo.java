import java.io.*;
import java.util.zip.*;
import net.minecraft.nbt.*;

public class SchemInfo {
    public static void main(String[] args) throws Exception {
        File f = new File(args[0]);
        byte[] raw;
        try (FileInputStream fis = new FileInputStream(f);
             GZIPInputStream gz = new GZIPInputStream(fis);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = gz.read(buf)) != -1) bos.write(buf, 0, n);
            raw = bos.toByteArray();
        }
        // Skip NBT header: 1 byte tag type + 2 bytes name length + name bytes
        int pos = 3 + 9; // type=10(COMPOUND) + nameLen=9("Schematic") + 9 bytes name
        // Read remaining as compound tag
        java.io.DataInputStream dis = new java.io.DataInputStream(new ByteArrayInputStream(raw));
        // Read tag type and name
        byte type = dis.readByte();
        short nameLen = dis.readShort();
        byte[] nameBytes = new byte[nameLen];
        dis.readFully(nameBytes);
        String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println("Root: type=" + type + " name=" + name);
        
        // Now read the compound tag contents
        CompoundTag tag = readCompound(dis);
        System.out.println("Version: " + tag.getInt("Version"));
        System.out.println("DataVersion: " + tag.getInt("DataVersion"));
        System.out.println("Width: " + tag.getShort("Width"));
        System.out.println("Height: " + tag.getShort("Height"));
        System.out.println("Length: " + tag.getShort("Length"));
        int[] off = tag.getIntArray("Offset");
        System.out.println("Offset: [" + off[0] + ", " + off[1] + ", " + off[2] + "]");
        System.out.println("PaletteMax: " + tag.getInt("PaletteMax"));
        CompoundTag pal = tag.getCompound("Palette");
        System.out.println("Palette entries (" + pal.getAllKeys().size() + "):");
        int count = 0;
        for (String key : pal.getAllKeys()) {
            if (count++ < 15) System.out.println("  [" + pal.getInt(key) + "] " + key);
        }
        if (count > 15) System.out.println("  ... and " + (count - 15) + " more");
    }
    
    static CompoundTag readCompound(java.io.DataInputStream dis) throws Exception {
        CompoundTag tag = new CompoundTag();
        while (true) {
            byte t = dis.readByte();
            if (t == 0) break; // TAG_End
            short nl = dis.readShort();
            byte[] nb = new byte[nl];
            dis.readFully(nb);
            String n = new String(nb, java.nio.charset.StandardCharsets.UTF_8);
            if (t == 1) tag.putByte(n, dis.readByte());
            else if (t == 2) tag.putShort(n, dis.readShort());
            else if (t == 3) tag.putInt(n, dis.readInt());
            else if (t == 4) tag.putLong(n, dis.readLong());
            else if (t == 5) tag.putFloat(n, dis.readFloat());
            else if (t == 6) tag.putDouble(n, dis.readDouble());
            else if (t == 7) { int len = dis.readInt(); byte[] arr = new byte[len]; dis.readFully(arr); tag.putByteArray(n, arr); }
            else if (t == 8) { short sl = dis.readShort(); byte[] sb = new byte[sl]; dis.readFully(sb); tag.putString(n, new String(sb, java.nio.charset.StandardCharsets.UTF_8)); }
            else if (t == 9) { byte lt = dis.readByte(); int len = dis.readInt(); /* skip list */ for (int i=0;i<len;i++) skipTag(dis, lt); }
            else if (t == 10) tag.put(n, readCompound(dis));
            else if (t == 11) { int len = dis.readInt(); int[] arr = new int[len]; for (int i=0;i<len;i++) arr[i]=dis.readInt(); tag.putIntArray(n, arr); }
            else if (t == 12) { int len = dis.readInt(); long[] arr = new long[len]; for (int i=0;i<len;i++) arr[i]=dis.readLong(); tag.putLongArray(n, arr); }
            else { System.out.println("Unknown tag type: " + t); break; }
        }
        return tag;
    }
    
    static void skipTag(java.io.DataInputStream dis, byte type) throws Exception {
        if (type == 0) return;
        else if (type <= 6) { dis.skipBytes(type==1||type==2?2:type==3||type==4?4:type<=6?8:0); }
        else if (type == 7 || type == 11 || type == 12) { int len = dis.readInt(); dis.skipBytes(len * (type==7?1:4)); }
        else if (type == 8) { short sl = dis.readShort(); dis.skipBytes(sl); }
        else if (type == 9) { byte lt = dis.readByte(); int len = dis.readInt(); for (int i=0;i<len;i++) skipTag(dis, lt); }
        else if (type == 10) { while (true) { byte t = dis.readByte(); if (t==0) break; short nl=dis.readShort(); dis.skipBytes(nl); skipTag(dis, t); } }
    }
}
