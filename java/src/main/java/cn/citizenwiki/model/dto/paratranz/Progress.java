package cn.citizenwiki.model.dto.paratranz;

public class Progress {
    private Double translate;
    private Double review;
    private Double check;

    // Getters and Setters
    public Double getTranslate() {
        return translate;
    }

    public void setTranslate(Double translate) {
        this.translate = translate;
    }

    public Double getReview() {
        return review;
    }

    public void setReview(Double review) {
        this.review = review;
    }

    public Double getCheck() {
        return check;
    }

    public void setCheck(Double check) {
        this.check = check;
    }

    @Override
    public String toString() {
        return "Progress{" +
                "translate=" + translate +
                ", review=" + review +
                ", check=" + check +
                '}';
    }
}
