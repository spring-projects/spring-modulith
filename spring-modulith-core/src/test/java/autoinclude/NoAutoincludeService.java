package autoinclude;

import autoinclude.dtos.MyDTO;

@org.springframework.modulith.NamedInterface(name = "NoAutoincludeService", autoIncludeRelatedTypes = false)
public interface NoAutoincludeService {

    void doSomething(MyDTO dto);
}
