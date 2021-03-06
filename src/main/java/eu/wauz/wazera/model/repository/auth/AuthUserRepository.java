package eu.wauz.wazera.model.repository.auth;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import eu.wauz.wazera.model.entity.auth.User;

public interface AuthUserRepository extends CrudRepository<User, Integer> {

	User findByUsername(String username);

	List<User> findAllByOrderByIdAsc();
	
}
