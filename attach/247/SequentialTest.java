import loci.formats.FileStitcher;
public class SequentialTest {
  public static void main(String[] args) throws Exception {
    FileStitcher fs = new FileStitcher();
    for (int i=0; i<args.length; i++) fs.setId(args[i]);
  }
}
