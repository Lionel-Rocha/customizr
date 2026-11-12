package top.customizr.system.service;

public class PlayerService {
    public String createCharacter(){
        //cria um character, cria uma sheet em branco, assinala a sheet ao character
        return "sheet";
    }

    public String readCharacter(){
        //retorna sheet, inventário e bastion do character
        return "sheet";
    }

    //only master or player, self
    public String updateCharacter(){
        //atualiza apenas character name
        return "sheet";
    }

    //only master or player, self
    public String deleteCharacter(){
        //remove sheet, inventário e bastion - bastion primeiro, inventário depois, por fim sheet e character.
        return "sheet";
    }
}
