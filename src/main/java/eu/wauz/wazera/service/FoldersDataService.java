package eu.wauz.wazera.service;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import eu.wauz.wazera.WazeraTool;
import eu.wauz.wazera.model.data.docs.FolderData;
import eu.wauz.wazera.model.entity.docs.Document;
import eu.wauz.wazera.model.entity.docs.Folder;
import eu.wauz.wazera.model.entity.docs.FolderUserData;
import eu.wauz.wazera.model.repository.docs.DocumentRepository;
import eu.wauz.wazera.model.repository.docs.FolderRepository;
import eu.wauz.wazera.model.repository.docs.FolderUserDataRepository;

@Service
@Scope("singleton")
public class FoldersDataService {
	
	@Autowired
	private DocumentsDataService documentsService;
	
	@Autowired
	private AuthDataService authService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private FolderUserDataRepository folderUserDataRepository;

    private WazeraTool wazeraTool;

    @PostConstruct
    public void init() {
    	wazeraTool = new WazeraTool();
    }

	public FolderData saveFolder(FolderData folderData, Integer index) throws Exception {
		wazeraTool.checkForValidFileName(folderData.getName());

        Folder folder = null;
        if(folderData.getId() != null) {
        	folder = folderRepository.findById(folderData.getId()).orElse(null);
        }
        else {
        	folder = new Folder();
        	index = 0;
        }
        folder.setName(folderData.getName());
        if(folderData.getParent() != null) {
        	folder.setFolderId(folderData.getParent().getId());
        }
        folder = folderRepository.save(folder);
        folderData.setId(folder.getId());
        
		if(index != null) {
			sortFolders(folder, index);
		}
		
		Integer userId = authService.getLoggedInUserId();
        saveFolderUserData(folderData, userId);
        return folderData;
    }
	
	private void sortFolders(Folder folder, Integer index) throws Exception {
		List<Folder> sortFolders = folderRepository.findByFolderIdOrderBySortOrder(folder.getFolderId());

		Folder foundFolder = null;
		for(Folder sortFolder : sortFolders) {
			if(sortFolder.getId().equals(folder.getId())) {
				foundFolder = sortFolder;
				break;
			}
		}
		if(foundFolder == null) {
			return;
		}
		sortFolders.remove(foundFolder);
		sortFolders.add(Math.max(0, Math.min(sortFolders.size(), index)), foundFolder);
		
		int currentId = 1;
		for(Folder sortFolder : sortFolders) {
			sortFolder.setSortOrder(currentId);
			currentId++;
		}
		folderRepository.saveAll(sortFolders);
	}

	private void saveFolderUserData(FolderData folderData, Integer userId) {
		FolderUserData folderUserDataFromRepo = folderUserDataRepository.findByFolderIdAndUserId(folderData.getId(), userId);
		FolderUserData folderUserData = folderUserDataFromRepo != null ? folderUserDataFromRepo : new FolderUserData();
		folderUserData.setUserId(userId);
		folderUserData.setFolderId(folderData.getId());
		folderUserData.setExpanded(folderData.isExpanded() != null ? folderData.isExpanded() : false);
		folderUserDataRepository.save(folderUserData);
	}

	public FolderData readFolderData(Folder folder, Integer userId) {
		FolderData folderData = new FolderData();
		if(folder != null) {
			folderData.setId(folder.getId());
			folderData.setName(folder.getName());
			FolderUserData folderUserData = folderUserDataRepository.findByFolderIdAndUserId(folder.getId(), userId);
			folderData.setExpanded(folderUserData != null ? folderUserData.getExpanded() : false);
			folderData.setSortOrder(folder.getSortOrder());
		}
		return folderData;
	}
	
	public String getFolderName(Integer folderId) {
		return folderRepository.findById(folderId).get().getName();
	}

	public FolderData getRootFolder() {
        Folder rootFolder = folderRepository.findRootFolder();
        if(rootFolder == null) {
        	rootFolder = new Folder();
        	rootFolder.setName("Document Tree");
        	rootFolder = folderRepository.save(rootFolder);
        }
        return readFolderData(rootFolder, 0);
	}

	public void deleteTree(int treeId) throws Exception {
        deleteSubtree(treeId);
	}

	public void deleteFolder(FolderData folderData) throws Exception {
        deleteSubtree(folderData.getId());
	}
	
	private void deleteSubtree(int rootNodeId) throws Exception {
		List<Folder> foldersToSearch = new ArrayList<>();
    	List<Folder> foldersToDelete = new ArrayList<>();
    	List<Document> documentsToDelete = new ArrayList<>();
		foldersToSearch.add(folderRepository.findById(rootNodeId).orElse(null));
		while(!foldersToSearch.isEmpty()) {
			Folder folder = foldersToSearch.remove(foldersToSearch.size() - 1);
			foldersToDelete.add(folder);
			
			List<Folder> subFolders = folderRepository.findByFolderIdOrderByName(folder.getId());
			foldersToSearch.addAll(subFolders);
			
			List<Document> documents = documentRepository.findByFolderIdOrderByName(folder.getId());
			documentsToDelete.addAll(documents);
		}
		
		for (Document document : documentsToDelete) {
			documentsService.deleteDocument(document.getId());
		}
		for (Folder folder : foldersToDelete) {
			folderRepository.delete(folder);
			folderUserDataRepository.deleteAll(folderUserDataRepository.findByFolderId(folder.getId()));
		}
	}

}
