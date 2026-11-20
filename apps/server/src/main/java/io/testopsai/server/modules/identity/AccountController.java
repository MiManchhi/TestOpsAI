package io.testopsai.server.modules.identity;

import io.testopsai.server.platform.security.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/account")
class AccountController {
  private final AccountService accounts;
  private final CurrentUserResolver users;

  AccountController(AccountService accounts, CurrentUserResolver users) {
    this.accounts = accounts;
    this.users = users;
  }

  @GetMapping
  Account myAccount(Authentication authentication) {
    return Account.from(users.resolve(authentication));
  }

  @PostMapping("/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changePassword(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody AccountService.PasswordChange body) {
    accounts.changePassword(users.resolve(authentication), key, body);
  }
}
