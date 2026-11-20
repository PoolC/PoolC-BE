package org.poolc.api.gamification.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AdminPokemonGrantRequest {
    private String loginID;
    private Long collectibleId;
    private boolean shiny;
}
