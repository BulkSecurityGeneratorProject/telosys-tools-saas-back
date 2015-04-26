package org.telosystools.saas.service.impl;

import com.mongodb.MongoException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.telosystools.saas.Application;
import org.telosystools.saas.bean.Path;
import org.telosystools.saas.config.MongoConfiguration;
import org.telosystools.saas.dao.FileDao;
import org.telosystools.saas.dao.WorkspaceDao;
import org.telosystools.saas.domain.File;
import org.telosystools.saas.domain.*;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Integration Test : workspace service
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@Import(MongoConfiguration.class)
public class WorkspaceServiceIntTest {

    private static final String PROJECT = "PROJECT_ID1";
    private static final String PROJECT2 = "PROJECT_ID2";
    private static final String FOLDER_NAME = "MY_FOLDER";
    private static final String SUBFOLDER_NAME = "MY_SUBFOLDER";
    private static final String FOLDER_PATH = Workspace.GENERATEDS+"/"+ FOLDER_NAME;
    private static final String SUBFOLDER_PATH = FOLDER_PATH+"/"+SUBFOLDER_NAME;
    private static final String MODIFIED_FOLDER_NAME = "RENAME_FOLDER";
    private static final String MODIFIED_SUBFOLDER_NAME = "RENAME_SUBFOLDER";
    private static final String MODIFIED_FOLDER_PATH = Workspace.GENERATEDS+"/"+ MODIFIED_FOLDER_NAME;
    private static final String MODIFIED_SUBFOLDER_PATH = FOLDER_PATH+"/"+MODIFIED_SUBFOLDER_NAME;
    private static final String FILE_NAME = "MY_FILE.java";
    private static final String FILE_PATH = Workspace.MODELS+ "/"+ FILE_NAME;
    private static final String FILE_EXT = "java";
    private static final String MODIFIED_FILE_NAME = "RENAMED_FILE";
    private static final String MODIFIED_FILE_PATH = Workspace.MODELS+"/"+ MODIFIED_FILE_NAME;
    private static final String MODIFIED_RESOURCE_FILE = "modified_file.txt";
    public static final String FILE_CONTENT = "Fichier example";
    public static final String MODIFIED_FILE_CONTENT = "fichier modifié";

    @Autowired
    WorkspaceServiceImpl workspaceService;

    WorkspaceDao workspaceDao;

    FileDao fileDao;

    @Before
    public void setUp() throws Exception {
        Field wsDaoField = WorkspaceServiceImpl.class.getDeclaredField("workspaceDao");
        wsDaoField.setAccessible(true);
        workspaceDao = (WorkspaceDao) wsDaoField.get(workspaceService);

        Field fileDaoField = WorkspaceServiceImpl.class.getDeclaredField("fileDao");
        fileDaoField.setAccessible(true);
        fileDao = (FileDao) fileDaoField.get(workspaceService);

        workspaceDao.save(buildWorkspace(), PROJECT);
    }

    @After
    public void tearDown() throws Exception {
        workspaceDao.delete(PROJECT);
    }

    @Test
    public void testCreateWorkspace() throws Exception {
        Workspace expected = workspaceService.createWorkspace(PROJECT2);
        Workspace actual = workspaceDao.load(PROJECT2);
        assertNotNull(actual);
        assertEquals(expected,actual);
        workspaceDao.delete(PROJECT2);
    }

    @Test
    public void testSaveWorkspace() throws Exception {
        Workspace expected = buildWorkspace();
        workspaceService.saveWorkspace(expected, PROJECT);
        Workspace actual = workspaceDao.load(PROJECT);
        assertNotNull(actual);
        assertEquals(expected,actual);
    }

    @Test
    public void testGetWorkspace() throws Exception {
        Workspace expected = buildWorkspace();
        workspaceDao.save(expected, PROJECT);
        Workspace actual = workspaceService.getWorkspace(PROJECT);
        assertNotNull(actual);
        assertEquals(expected,actual);
    }

    @Test
    public void testCreateFolder() throws Exception {
        Folder expected = workspaceService.createFolder(FOLDER_PATH, PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);
        Folder actual = workspaceService.getFolderForPath(workspace, Path.valueOf(FOLDER_PATH));
        assertNotNull(actual);
        assertEquals(expected,actual);
    }

    @Test
    public void testRenameFolder() throws Exception {
        Folder expectedFolder = workspaceService.createFolder(FOLDER_PATH, PROJECT);
        workspaceService.renameFolder(expectedFolder, MODIFIED_FOLDER_NAME, PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);

        Folder actualFolder = workspaceService.getFolderForPath(workspace, Path.valueOf(MODIFIED_FOLDER_PATH));
        assertNotNull(actualFolder);
        assertEquals(expectedFolder,actualFolder);
    }

    @Test
    public void testRemoveFolder() throws Exception {
        workspaceService.createFolder(FOLDER_PATH, PROJECT);
        workspaceService.removeFolder(FOLDER_PATH, PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);

        Folder absent = workspaceService.getFolderForPath(workspace, Path.valueOf(FOLDER_PATH));
        assertNull(absent);
    }

    @Test
    public void testCreateFile() throws Exception {
        File expectedFile = workspaceService.createFile(FILE_PATH, FILE_CONTENT, PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);

        File actualFile = workspaceService.getFileForPath(workspace, Path.valueOf(FILE_PATH));
        assertNotNull(actualFile);
        assertEquals(expectedFile,actualFile);

        InputStream actualIn = fileDao.loadContent(actualFile.getGridFSId(), PROJECT);
        assertNotNull(actualIn);
        assertEqualsInputStream(createInputStream(FILE_CONTENT), actualIn);
    }


    @Test
    public void testSaveFile_modify() throws Exception {
        File expectedFile = workspaceService.createFile(FILE_PATH, FILE_CONTENT, PROJECT);

        Workspace workspace = workspaceDao.load(PROJECT);
        File actualFile = workspaceService.getFileForPath(workspace, Path.valueOf(FILE_PATH));
        assertNotNull(actualFile);
        assertEquals(expectedFile, actualFile);

        String newFileId = workspaceService.updateFileContent(PROJECT, expectedFile.getGridFSId(), MODIFIED_FILE_CONTENT);

        String actualIn = workspaceService.getFileContent(PROJECT, newFileId);
        assertNotNull(actualIn);
        assertEquals(MODIFIED_FILE_CONTENT, actualIn.trim());
    }

    @Test
    public void testRenameFile() throws Exception {
        File expectedFile = workspaceService.createFile(FILE_PATH, FILE_CONTENT, PROJECT);
        workspaceService.renameFile(expectedFile, MODIFIED_FILE_NAME, PROJECT);

        Workspace workspace = workspaceDao.load(PROJECT);
        File actualFile = workspaceService.getFileForPath(workspace, Path.valueOf(MODIFIED_FILE_PATH));
        assertNotNull(actualFile);
        assertEquals(expectedFile,actualFile);
    }

    @Test(expected = MongoException.class)
    public void testRemoveFile() throws Exception {
        File file = workspaceService.createFile(FILE_PATH, FILE_CONTENT, PROJECT);
        workspaceService.removeFile(FILE_PATH,PROJECT);
        workspaceService.getFileContent(PROJECT, file.getGridFSId());
    }

    @Test
    public void testExists() {
        File file = null;
        try {
            file = workspaceService.createFile(FILE_PATH, FILE_CONTENT, PROJECT);
        } catch (FolderNotFoundException e) {
            fail(e.getMessage());
        }

        workspaceService.removeFile(FILE_PATH,PROJECT);
        assertFalse(workspaceService.exists(workspaceService.getWorkspace(PROJECT), file.getAbsolutePath()));
    }

    @Test
    public void testGetFileExtension() {
        File file = null;
        try {
            file = workspaceService.createFile(FILE_PATH, FILE_CONTENT, PROJECT);
        } catch (FolderNotFoundException e) {
            fail(e.getMessage());
        }
        assertEquals(FILE_EXT, workspaceService.getFileExtension(file.getName()));
    }

    @Test
    public void testCreateSubFolder() throws Exception {
        workspaceService.createFolder(FOLDER_PATH,PROJECT);
        Folder expected = workspaceService.createFolder(SUBFOLDER_PATH, PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);
        Folder actual = workspaceService.getFolderForPath(workspace, Path.valueOf(SUBFOLDER_PATH));
        assertNotNull(actual);
        assertEquals(expected,actual);
    }

    @Test
    public void testRenameSubFolder() throws Exception {
        workspaceService.createFolder(FOLDER_PATH,PROJECT);
        Folder expectedFolder = workspaceService.createFolder(SUBFOLDER_PATH, PROJECT);
        workspaceService.renameFolder(expectedFolder, MODIFIED_SUBFOLDER_NAME, PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);

        Folder actualFolder = workspaceService.getFolderForPath(workspace, Path.valueOf(MODIFIED_SUBFOLDER_PATH));
        assertNotNull(actualFolder);
        assertEquals(expectedFolder,actualFolder);
    }

    @Test
    public void testRemoveSubFolder() throws Exception {
        workspaceService.createFolder(FOLDER_PATH,PROJECT);
        workspaceService.createFolder(SUBFOLDER_PATH, PROJECT);
        workspaceService.removeFolder(SUBFOLDER_PATH,PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);

        Folder absent = workspaceService.getFolderForPath(workspace, Path.valueOf(SUBFOLDER_PATH));
        assertNull(absent);
    }

    @Test
    public void testCreateFileInSubFolder() throws Exception {
        workspaceService.createFolder(FOLDER_PATH,PROJECT);
        String filePath = FOLDER_PATH+"/"+FILE_NAME;
        File expectedFile = workspaceService.createFile(filePath, FILE_CONTENT, PROJECT);
        Workspace workspace = workspaceDao.load(PROJECT);

        File actualFile = workspaceService.getFileForPath(workspace, Path.valueOf(filePath));
        assertNotNull(actualFile);
        assertEquals(expectedFile,actualFile);

        String actualIn = workspaceService.getFileContent(PROJECT, actualFile.getGridFSId());
        assertNotNull(actualIn);
        assertEquals(FILE_CONTENT, actualIn.trim());
    }

    @Test
    public void testSaveFileInSubFolder() throws Exception {
        workspaceService.createFolder(FOLDER_PATH,PROJECT);
        String filePath = FOLDER_PATH+"/"+FILE_NAME;
        File expectedFile = workspaceService.createFile(filePath, FILE_CONTENT, PROJECT);
        InputStream expectedIn = getInputStream(MODIFIED_RESOURCE_FILE);

        workspaceService.saveFile(expectedFile, expectedIn, PROJECT);

        Workspace workspace = workspaceDao.load(PROJECT);
        File actualFile = workspaceService.getFileForPath(workspace, Path.valueOf(filePath));
        assertNotNull(actualFile);
        assertEquals(expectedFile, actualFile);

        String actualIn = workspaceService.getFileContent(PROJECT, actualFile.getGridFSId());
        assertNotNull(actualIn);
        assertEquals(FILE_CONTENT, actualIn.trim());
    }

    @Test
    public void testRenameFileInSubFolder() throws Exception {
        workspaceService.createFolder(FOLDER_PATH,PROJECT);
        String filePath = FOLDER_PATH+"/"+FILE_NAME;
        File expectedFile = workspaceService.createFile(filePath, FILE_CONTENT, PROJECT);
        workspaceService.renameFile(expectedFile, MODIFIED_FILE_NAME, PROJECT);

        Workspace workspace = workspaceDao.load(PROJECT);
        String modifiedFilePath = FOLDER_PATH+"/"+MODIFIED_FILE_NAME;
        File actualFile = workspaceService.getFileForPath(workspace, Path.valueOf(modifiedFilePath));
        assertNotNull(actualFile);
        assertEquals(expectedFile,actualFile);
    }

    @Test(expected = MongoException.class)
    public void testRemoveFileInSubFolder() throws Exception {
        File file = workspaceService.createFile(FILE_PATH, FILE_CONTENT, PROJECT);
        workspaceService.removeFile(FILE_PATH,PROJECT);
        workspaceService.getFileContent(PROJECT, file.getGridFSId());
    }


    private Workspace buildWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setModels(new RootFolder(Workspace.MODELS));
        workspace.setTemplates(new RootFolder(Workspace.TEMPLATES));
        workspace.setGenerateds(new RootFolder(Workspace.GENERATEDS));
        workspace.setSettings(new RootFolder(Workspace.SETTINGS));
        return workspace;
    }

    private InputStream getInputStream(String fileName) throws FileNotFoundException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        return classLoader.getResourceAsStream(fileName);
    }

    private InputStream createInputStream(String string) {
        return new ByteArrayInputStream(string.getBytes());
    }

    private void assertEqualsInputStream(InputStream expectedIn, InputStream actualIn) {
        BufferedReader actualReader = new BufferedReader(new InputStreamReader(actualIn, StandardCharsets.UTF_8));
        BufferedReader expectedReader = new BufferedReader(new InputStreamReader(expectedIn, StandardCharsets.UTF_8));
        try {
            String actualLine = actualReader.readLine();
            String expected = expectedReader.readLine();
            while ((actualLine) != null) {
                assertEquals(expected, actualLine);
                actualLine = actualReader.readLine();
                expected = expectedReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}