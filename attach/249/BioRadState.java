import loci.formats.FileStitcher;
public class BioRadState {
  public static void main(String[] args) throws Exception {
    String id1 = args[0];
    String id2 = args[1];
    System.out.println("Initializing " + id1);
    FileStitcher r1 = new FileStitcher();
    r1.setId(id1);
    System.out.println("SizeZ = " + r1.getSizeZ());
    System.out.println("Initializing " + id2 + " (fresh reader)");
    FileStitcher r2 = new FileStitcher();
    r2.setId(id2);
    System.out.println("SizeZ = " + r2.getSizeZ());
    System.out.println("Initializing " + id2 + " (stale reader)");
    r1.setId(id2);
    System.out.println("SizeZ = " + r1.getSizeZ());
  }
}
