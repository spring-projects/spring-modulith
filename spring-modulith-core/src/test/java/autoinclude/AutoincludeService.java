package autoinclude;

import autoinclude.dtos.MyDTO;

@org.springframework.modulith.NamedInterface(name = "AutoincludeService", autoIncludeRelatedTypes = true)
public interface AutoincludeService {

    void doSomething(MyDTO dto);
}
