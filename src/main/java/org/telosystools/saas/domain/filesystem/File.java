package org.telosystools.saas.domain.filesystem;

import org.telosystools.saas.bean.Path;

import java.io.Serializable;

/**
 * File : gridFSId is the identifier of the file in GridFS.
 */
public class File implements Serializable {

    public static final String FILE_EXTENSION_UNKNOWN = "[unknown]";

    /**
     * Path to the file with the file name
     */
    private String absolutePath;
    /**
     * Path to the file
     */
    private String path;
    /**
     * Name of the file (contains the file extension)
     */
    private String name;
    /**
     * File extension
     */
    private String ext;
    /**
     * GridFS identifier
     */
    private String gridFSId;

    File() {}

    public File(String absolutePath) {
        this.absolutePath = absolutePath;
        Path path = Path.valueOf(absolutePath);
        this.path = path.getBasename();
        this.name = path.getFilename();
        this.ext = getFileExtension(name);
    }

    public File(Path path) {
        this.absolutePath = path.toString();
        this.path = path.getBasename();
        this.name = path.getFilename();
        this.ext = getFileExtension(name);
    }

    public void updatePath(Path newPath) {
        this.absolutePath = Path.join(newPath.toString(), this.name);
        this.path = newPath.getBasename();
    }


    /**
     * Return the file extension from the file name.
     * @param filename file name
     * @return file extension
     */
    public static String getFileExtension(String filename) {
        if(filename == null) {
            return null;
        }
        int posDot = filename.lastIndexOf(".");
        if(posDot != -1) {
            return filename.substring(posDot+1);

        }
        return File.FILE_EXTENSION_UNKNOWN;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public String getGridFSId() {
        return gridFSId;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setGridFSId(String gridFSId) {
        this.gridFSId = gridFSId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof File)) return false;

        File file = (File) o;

        return absolutePath.equals(file.absolutePath)
                && ext.equals(file.ext)
                && gridFSId.equals(file.gridFSId)
                && name.equals(file.name)
                && path.equals(file.path);
    }

    @Override
    public int hashCode() {
        int result = absolutePath.hashCode();
        result = 31 * result + path.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + ext.hashCode();
        result = 31 * result + gridFSId.hashCode();
        return result;
    }

    public void changeName(String name) {
        this.name = name;
        this.absolutePath = this.path+"/"+name;
        this.ext = getFileExtension(name);
    }
}
