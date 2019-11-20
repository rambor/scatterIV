package version4.sasCIF;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties("hidden")
public interface Hidable {
    boolean isHidden();
}
