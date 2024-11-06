package cn.citizenwiki.model.dto.paratranz;

import java.time.ZonedDateTime;

public class PZFile {
    private Long id;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime modifiedAt;
    private String name;
    private Integer project;
    private String format;
    private Integer total;
    private Integer translated;
    private Integer disputed;
    private Integer checked;
    private Integer reviewed;
    private Integer hidden;
    private Integer locked;
    private Integer words;
    private String hash;
    private String folder;
    private Progress progress;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public ZonedDateTime getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(ZonedDateTime modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getProject() {
        return project;
    }

    public void setProject(Integer project) {
        this.project = project;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getTranslated() {
        return translated;
    }

    public void setTranslated(Integer translated) {
        this.translated = translated;
    }

    public Integer getDisputed() {
        return disputed;
    }

    public void setDisputed(Integer disputed) {
        this.disputed = disputed;
    }

    public Integer getChecked() {
        return checked;
    }

    public void setChecked(Integer checked) {
        this.checked = checked;
    }

    public Integer getReviewed() {
        return reviewed;
    }

    public void setReviewed(Integer reviewed) {
        this.reviewed = reviewed;
    }

    public Integer getHidden() {
        return hidden;
    }

    public void setHidden(Integer hidden) {
        this.hidden = hidden;
    }

    public Integer getLocked() {
        return locked;
    }

    public void setLocked(Integer locked) {
        this.locked = locked;
    }

    public Integer getWords() {
        return words;
    }

    public void setWords(Integer words) {
        this.words = words;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public Progress getProgress() {
        return progress;
    }

    public void setProgress(Progress progress) {
        this.progress = progress;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "id=" + id +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", modifiedAt=" + modifiedAt +
                ", name='" + name + '\'' +
                ", project=" + project +
                ", format='" + format + '\'' +
                ", total=" + total +
                ", translated=" + translated +
                ", disputed=" + disputed +
                ", checked=" + checked +
                ", reviewed=" + reviewed +
                ", hidden=" + hidden +
                ", locked=" + locked +
                ", words=" + words +
                ", hash='" + hash + '\'' +
                ", folder='" + folder + '\'' +
                ", progress=" + progress +
                '}';
    }
}
