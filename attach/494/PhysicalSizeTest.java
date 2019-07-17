import loci.formats.*;
import loci.formats.meta.*;

public class PhysicalSizeTest {
  public static void main(String[] args) throws Exception {
    ImageReader reader = new ImageReader();
    IMetadata metadata = MetadataTools.createOMEXMLMetadata();
    reader.setMetadataStore(metadata);
    reader.setId(args[0]);

    Double xd = metadata.getPixelsPhysicalSizeX(0);
    Double yd = metadata.getPixelsPhysicalSizeY(0);
    Double zd = metadata.getPixelsPhysicalSizeZ(0);

    System.out.println("PhysicalSizeX = " + xd);
    System.out.println("PhysicalSizeY = " + yd);
    System.out.println("PhysicalSizeZ = " + zd);
  }
}
