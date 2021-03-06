package org.telosystools.saas.service.impl;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telosystools.saas.bean.Path;
import org.telosystools.saas.dao.FileDao;
import org.telosystools.saas.dao.RootFolderDao;
import org.telosystools.saas.dao.WorkspaceDao;
import org.telosystools.saas.domain.filesystem.*;
import org.telosystools.saas.exception.*;
import org.telosystools.saas.service.WorkspaceService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Created by luchabou on 27/02/2015.
 * <p/>
 * Service de gestion du workspace contenant
 * des folders et des fichiers
 */
@Component
public class WorkspaceServiceImpl implements WorkspaceService {

    public static final String REGEX_FILENAME = "^([_A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)?)$";
    public static final String REGEX_FOLDER = "[^_A-Za-z0-9/\\-]";
    public static final String REGEX_FOLDERS = REGEX_FOLDER + "*";

    private final Logger log = LoggerFactory.getLogger(WorkspaceServiceImpl.class);

    @Autowired
    private WorkspaceDao workspaceDao;
    @Autowired
    private FileDao fileDao;
    @Autowired
    private RootFolderDao rootFolderDao;

    @Override
    public Workspace createWorkspace(String projectId) {
        Workspace workspace = new Workspace();

        workspace.setModel(new RootFolder(Workspace.MODEL));
        workspace.setTemplates(new RootFolder(Workspace.TEMPLATES));
        workspace.setGenerated(new RootFolder(Workspace.GENERATED, true));

        workspaceDao.save(workspace, projectId);

        return workspace;
    }

    @Override
    public void saveWorkspace(Workspace workspace, String projectId) {
        workspaceDao.save(workspace, projectId);
    }

    @Override
    public Workspace getWorkspace(String projectId) throws ProjectNotFoundException {
        Workspace workspace = workspaceDao.load(projectId);
        if (workspace != null) {
            return workspace;
        } else {
            throw new ProjectNotFoundException(projectId);
        }
    }

    @Override
    public RootFolder createFolder(String absolutePath, String projectId) throws FolderNotFoundException, ProjectNotFoundException, InvalidPathException, DuplicateResourceException {
        if (absolutePath.matches(REGEX_FOLDERS)) throw new InvalidPathException(absolutePath);

        Workspace workspace = getWorkspace(projectId);
        Path path = Path.valueOf(absolutePath);

        Folder folderParent = getFolderForPath(workspace, path.getParent());
        if (folderParent == null)
            throw new FolderNotFoundException(path.getBasename(), projectId);

        if (getFolderForPath(workspace, path) != null)
            throw new DuplicateResourceException(absolutePath);

        Folder folder = new Folder(path);
        folderParent.addFolder(folder);
        workspaceDao.save(workspace, projectId);
        return this.getRootFolderForPath(workspace, path);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RootFolder renameFolder(String absolutePath, String folderName, String projectId) throws ProjectNotFoundException, InvalidPathException, FolderNotFoundException {
        if (folderName.matches(REGEX_FOLDER)) throw new InvalidPathException(folderName);
        if (absolutePath.matches(REGEX_FOLDERS)) throw new InvalidPathException(absolutePath);

        Workspace workspace = getWorkspace(projectId);
        Path path = Path.valueOf(absolutePath);

        Folder folder = getFolderForPath(workspace, path);
        if (folder == null) throw new FolderNotFoundException(absolutePath, projectId);

        Folder folderParent = getFolderForPath(workspace, path.getParent());
        folderParent.removeFolder(folder);
        folder.changeName(folderName);
        folderParent.addFolder(folder);

        workspaceDao.save(workspace, projectId);
        return this.getRootFolderForPath(workspace, path);
    }

    /**
     * Remove an existing folder.
     *
     * @param projectId    project unique identifier
     * @param absolutePath the path of the folder
     */
    @Override
    public RootFolder removeFolder(String absolutePath, String projectId) throws ProjectNotFoundException, FolderNotFoundException, InvalidPathException {
        Workspace workspace = getWorkspace(projectId);
        Path path = Path.valueOf(absolutePath);

        if (path.getBasename().matches(REGEX_FOLDERS)) throw new InvalidPathException(absolutePath);

        Folder folder = getFolderForPath(workspace, path);

        if (folder == null)
            throw new FolderNotFoundException(path.getBasename(), projectId);

        for (File file : folder.getFiles().values()) {
            fileDao.remove(file, projectId);
        }
        Folder folderParent = getFolderForPath(workspace, path.getParent());
        folderParent.getFolders().remove(folder.getName());
        workspaceDao.save(workspace, projectId);
        return this.getRootFolderForPath(workspace, path);
    }

    /**
     * Create a new file in an existing folder.
     *
     * @param absolutePath Absolute path
     * @param content      File content as String
     * @param projectId    Project id
     */
    @Override
    public RootFolder createFile(String absolutePath, String content, String projectId) throws FolderNotFoundException, FileNotFoundException, ProjectNotFoundException, InvalidPathException, DuplicateResourceException {
        Path path = Path.valueOf(absolutePath);

        if (path.getBasename().matches(REGEX_FOLDERS)) throw new InvalidPathException(absolutePath);
        if (!path.getFilename().matches(REGEX_FILENAME)) throw new InvalidPathException(path.getFilename());

        Workspace workspace = getWorkspace(projectId);
        Folder folderParent = getFolderForPath(workspace, path.getParent());

        if (folderParent == null)
            throw new FolderNotFoundException(path.getBasename(), projectId);

        if (getFileForPath(workspace, path) != null)
            throw new DuplicateResourceException(absolutePath);

        File file = new File(path);
        folderParent.addFile(file);
        fileDao.save(file, this.createInputStream(content == null ? "Text sample" : content), projectId);
        workspaceDao.save(workspace, projectId);

        return this.getRootFolderForPath(workspace, path);
    }

    /**
     * Rename an existing file
     *
     * @param absolutePath the path to the file to be renamed
     * @param fileName  the new name of the file
     * @param projectId project unique identifier
     */
    @Override
    public RootFolder renameFile(String absolutePath, String fileName, String projectId) throws ProjectNotFoundException, InvalidPathException, FileNotFoundException {
        Workspace workspace = getWorkspace(projectId);
        Path path = Path.valueOf(absolutePath);

        if (path.getBasename().matches(REGEX_FOLDERS)) throw new InvalidPathException(absolutePath);
        if (!path.getFilename().matches(REGEX_FILENAME)) throw new InvalidPathException(path.getFilename());

        File file = getFileForPath(workspace, path);
        if (file == null) throw new FileNotFoundException(absolutePath);
        if (file.getName().equals(fileName)) return this.getRootFolderForPath(workspace, path);

        Folder folderParent = getFolderForPath(workspace, path.getParent());
        folderParent.removeFile(file);
        file.changeName(fileName);
        folderParent.addFile(file);

        workspaceDao.save(workspace, projectId);
        return this.getRootFolderForPath(workspace, path);
    }

    /**
     * Remove file from the folder.
     *
     * @param absolutePath Absolute path
     * @param projectId    Project id
     */
    @Override
    public RootFolder removeFile(String absolutePath, String projectId) throws ProjectNotFoundException, InvalidPathException, FileNotFoundException {
        Workspace workspace = getWorkspace(projectId);
        Path path = Path.valueOf(absolutePath);

        if (path.getBasename().matches(REGEX_FOLDERS)) throw new InvalidPathException(absolutePath);
        if (!path.getFilename().matches(REGEX_FILENAME)) throw new InvalidPathException(path.getFilename());

        File file = getFileForPath(workspace, path);
        if (file == null) throw new FileNotFoundException(absolutePath);
        Folder folderParent = getFolderForPath(workspace, path.getParent());

        fileDao.remove(file, projectId);
        folderParent.removeFile(file);

        workspaceDao.save(workspace, projectId);
        return this.getRootFolderForPath(workspace, path);
    }

    /**
     * Indicates if the file exists ot not
     *
     * @param workspace workspace
     * @param path      path
     * @return boolean
     */
    public boolean exists(Workspace workspace, String path) {
        return getFolderForPath(workspace, Path.valueOf(path)) != null && getFileForPath(workspace, Path.valueOf(path)) != null;
    }

    /**
     * Get sub folder of the folder belong the path
     *
     * @param path Path
     * @return Sub folder
     */
    public Folder getFolderForPath(Workspace workspace, Path path) {
        Folder currentFolder = getRootFolderForPath(workspace, path);
        for (int i = 1; i < path.getNameCount(); i++) {
            String name = path.getName(i);
            if (currentFolder.getFolders().containsKey(name)) {
                currentFolder = currentFolder.getFolders().get(name);
            } else {
                return null;
            }
        }
        return currentFolder;
    }

    /**
     * Get sub folder of the folder belong the path
     *
     * @param path Path
     * @return Sub folder
     */
    public File getFileForPath(Workspace workspace, Path path) {
        Folder currentFolder = getRootFolderForPath(workspace, path);
        if (path.getNameCount() > 1) {
            for (int i = 1; i < path.getNameCount() - 1; i++) {
                String name = path.getName(i);
                if (currentFolder.getFolders().containsKey(name)) {
                    currentFolder = currentFolder.getFolders().get(name);
                } else {
                    return null;
                }
            }
        }
        if (path.getNameCount() > 0) {
            String name = path.getName(path.getNameCount() - 1).replace('.', Folder.DOT_REPLACEMENT);
            if (currentFolder.getFiles().containsKey(name)) {
                return currentFolder.getFiles().get(name);
            } else {
                return null;
            }
        }
        return null;
    }

    @Override
    public FileData getFileContent(String absolutePath, String projectId) throws ProjectNotFoundException, FileNotFoundException {
        final Workspace workspace = this.getWorkspace(projectId);
        final File file = this.getFileForPath(workspace, Path.valueOf(absolutePath));

        if (file == null) throw new FileNotFoundException("File not found in path");

        String content = "";
        try {
            content = IOUtils.toString(fileDao.loadContent(file.getGridFSId(), projectId), UTF_8);
        } catch (IOException e) {
            log.error("Failed to convert from Inputstream while retrieving file content for path : " + absolutePath);
        }

        return new FileData(file.getAbsolutePath(), content, file.getName());
    }

    @Override
    public void updateFile(String absolutePath, String content, String projectId) throws ProjectNotFoundException, FileNotFoundException {
        final Workspace workspace = this.getWorkspace(projectId);
        final Path parsedPath = Path.valueOf(absolutePath);
        final RootFolder rootFolder = getRootFolderForPath(workspace, parsedPath);
        final File file = this.getFileForPath(workspace, parsedPath);

        if (file == null) throw new FileNotFoundException("File not found in path");

        // Sauvegarde dans GridFS. L'id GridFS est mis à jour dans le File
        fileDao.save(file, createInputStream(content), projectId);
        // Mise à jour du workspace
        rootFolderDao.save(rootFolder, projectId);
    }

    @Override
    public void deleteWorkspace(String projectId) {
        workspaceDao.delete(projectId);
    }

    /**
     * Return the root folder corresponding to the path
     *
     * @param path Path
     * @return Root folder
     */
    public RootFolder getRootFolderForPath(Workspace workspace, Path path) {
        return workspace.getRootFolderByName(path.getRootName());
    }

    /**
     * Create an Inputstream from a String
     *
     * @param string String content
     * @return content as a stream
     */
    private InputStream createInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes());
    }

}
