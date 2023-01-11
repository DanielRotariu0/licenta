import { Injectable } from "@angular/core";
import { Router } from "@angular/router";
import { HttpClient } from "@angular/common/http";
import { BehaviorSubject, Observable } from "rxjs";
import { map } from "rxjs/operators";

import { environment } from "../../environments/environment";
import { User } from "../models";

@Injectable({ providedIn: "root" })
export class AccountService {
  private userSubject: BehaviorSubject<User | null>;
  public user: Observable<User | null>;

  constructor(private router: Router, private http: HttpClient) {
    this.userSubject = new BehaviorSubject(
      JSON.parse(localStorage.getItem("user")!)
    );
    this.user = this.userSubject.asObservable();
  }

  public get userValue() {
    return this.userSubject.value;
  }

  login(email: string, password: string) {
    return this.http
      .post<User>(`${environment.apiUrl}/login`, {
        email,
        password,
      })
      .pipe(
        map((user: User) => {
          // store user details and jwt token in local storage to keep user logged in between page refreshes
          let regularUserId = 1;
          let recruiterId = 2;

          if (user.roles.find((role) => role == recruiterId)) {
            user.isRecruiter = true;
          } else {
            user.isRecruiter = false;
          }
          localStorage.setItem("user", JSON.stringify(user));
          this.userSubject.next(user);
          return user;
        })
      );
  }

  logout() {
    // remove user from local storage and set current user to null
    localStorage.removeItem("user");
    this.userSubject.next(null);
    this.router.navigate(["/account/login"]);
  }

  register(user: User) {
    return this.http.post(`${environment.apiUrl}/users`, user);
  }

  getAll() {
    return this.http.get<User[]>(`${environment.apiUrl}/users`);
  }

  getById(id: number) {
    return this.http.get<User>(`${environment.apiUrl}/users/${id}`);
  }

  update(id: number, params: any) {
    return this.http.put(`${environment.apiUrl}/users/${id}`, params).pipe(
      map((x) => {
        // update stored user if the logged in user updated their own record
        if (id == this.userValue?.id) {
          // update local storage
          const user = { ...this.userValue, ...params };
          localStorage.setItem("user", JSON.stringify(user));

          // publish updated user to subscribers
          this.userSubject.next(user);
        }
        return x;
      })
    );
  }

  delete(id: number) {
    return this.http.delete(`${environment.apiUrl}/users/${id}`).pipe(
      map((x) => {
        // auto logout if the logged in user deleted their own record
        if (id == this.userValue?.id) {
          this.logout();
        }
        return x;
      })
    );
  }
}