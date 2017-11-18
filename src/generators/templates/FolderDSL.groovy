package templates


public class FolderDSL {

    static void createFolder_DSL(def job, def folder_fullPath) {
        job.with {
            folder(folder_fullPath) {
                description "Feature folder for " + folder_fullPath + " branch"
            }
        }
    }
}
