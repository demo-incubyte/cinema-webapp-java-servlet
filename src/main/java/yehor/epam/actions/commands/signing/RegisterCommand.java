package yehor.epam.actions.commands.signing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.log4j.Logger;
import yehor.epam.actions.BaseCommand;
import yehor.epam.dao.UserDAO;
import yehor.epam.dao.exception.RegisterException;
import yehor.epam.dao.factories.DAOFactory;
import yehor.epam.dao.factories.MySQLFactory;
import yehor.epam.entities.User;
import yehor.epam.services.CookieService;
import yehor.epam.services.ErrorService;
import yehor.epam.services.PassEncryptionManager;
import yehor.epam.services.VerifyService;
import yehor.epam.utilities.LoggerManager;
import yehor.epam.utilities.RedirectManager;

import static yehor.epam.utilities.CommandConstants.COMMAND_VIEW_PROFILE_PAGE;
import static yehor.epam.utilities.OtherConstants.USER_ID;
import static yehor.epam.utilities.OtherConstants.USER_ROLE;

public class RegisterCommand implements BaseCommand {
    private static final Logger logger = LoggerManager.getLogger(RegisterCommand.class);
    private static final String CLASS_NAME = RegisterCommand.class.getName();

    @Override
    public void execute(HttpServletRequest request, HttpServletResponse response) {
        try (DAOFactory factory = new MySQLFactory()) {
            logger.debug("Created DAOFactory in " + CLASS_NAME + " execute command");

            //captcha validation
            VerifyService verifyService = new VerifyService();
            verifyService.captchaValidation(request, response);

            final User user = getUserFromRequest(request);
            final UserDAO userDao = factory.getUserDao();
            final boolean inserted = userDao.insert(user);
            final int userId = userDao.getMaxId();
            user.setId(userId);

            if (inserted) {
                logger.debug("User was inserted");
                final HttpSession session = request.getSession(true);
                session.setAttribute(USER_ID, user.getId());
                session.setAttribute(USER_ROLE, user.getUserRole());
                final String rememberMe = request.getParameter("rememberMe");
                CookieService cookieService = new CookieService();
                cookieService.loginCookie(response, user, rememberMe);
                response.sendRedirect(RedirectManager.getRedirectLocation(COMMAND_VIEW_PROFILE_PAGE));
            } else {
                logger.debug("User wasn't inserted");
                throw new RegisterException("User wasn't added to Database");
            }
        } catch (Exception e) {
            ErrorService.handleException(request, response, CLASS_NAME, e);
        }
    }

    private User getUserFromRequest(HttpServletRequest request) {
        final String password = request.getParameter("password");
        PassEncryptionManager passManager = new PassEncryptionManager();
        String saltValue = passManager.getSaltValue(30);
        String securePassword = passManager.generateSecurePassword(password, saltValue);
        return new User(
                request.getParameter("firstName"),
                request.getParameter("secondName"),
                request.getParameter("email"),
                securePassword,
                request.getParameter("phoneNumber"),
                fromCheckboxToBoolean(request.getParameter("notification")),
                saltValue
        );
    }

    private boolean fromCheckboxToBoolean(String checkboxValue) {
        return checkboxValue != null && checkboxValue.equals("on");
    }
}
