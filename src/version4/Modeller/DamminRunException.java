package version4.Modeller;

/**
 * Created by robertrambo on 02/02/2016.
 */
public class DamminRunException extends Exception {

    String errorMessage;

    public DamminRunException(String text){
        this.errorMessage = text;
    }

    public String getErrorMessage(){
        return errorMessage;
    }
}
